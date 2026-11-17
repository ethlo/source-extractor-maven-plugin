package com.ethlo.maven.extractor;

/*-
 * #%L
 * source-extractor-maven-plugin
 * %%
 * Copyright (C) 2024 - 2026 Morten Haraldsen (ethlo)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.stmt.BlockStmt;

import com.ethlo.maven.extractor.model.ClassSample;
import com.ethlo.maven.extractor.model.MethodSample;
import com.ethlo.maven.extractor.model.SourcePosition;
import com.ethlo.maven.extractor.model.SourceRange;

/**
 * Parses Java source files and turns their methods into {@link MethodSample}s.
 *
 * <p>Every method with a body that is not {@code private} becomes a sample. Private methods are treated as
 * helpers and left out. Files are processed in path order, and methods in source order, so the output is stable.</p>
 */
public final class JavaSampleExtractor
{
    private final Path baseDir;
    private final Charset encoding;
    private final Consumer<String> warnings;

    /**
     * @param baseDir  the directory that paths in the result are made relative to, normally the project base directory
     * @param encoding the encoding of the source files
     * @param warnings receives a message for every file that could not be parsed
     */
    public JavaSampleExtractor(Path baseDir, Charset encoding, Consumer<String> warnings)
    {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.encoding = encoding;
        this.warnings = warnings;
    }

    /**
     * Extract all samples from the Java files in a directory and its subdirectories.
     *
     * @param directory the directory to scan
     * @return one entry per class that has at least one sample
     * @throws IOException if the directory cannot be read
     */
    public List<ClassSample> extract(Path directory) throws IOException
    {
        final List<Path> files;
        try (Stream<Path> walk = Files.walk(directory))
        {
            files = walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }

        final List<ClassSample> result = new ArrayList<>();
        for (Path file : files)
        {
            result.addAll(extractFile(file));
        }
        return result;
    }

    List<ClassSample> extractFile(Path file) throws IOException
    {
        final String source = Files.readString(file, encoding);
        final ParseResult<CompilationUnit> parsed = parser().parse(source);
        final Optional<CompilationUnit> cu = parsed.getResult().filter(r -> parsed.isSuccessful());
        if (cu.isEmpty())
        {
            warnings.accept("Skipping " + relative(file) + ", it could not be parsed: "
                    + String.join(", ", parsed.getProblems().stream().map(Problem::getVerboseMessage).toList()));
            return List.of();
        }

        final String path = relative(file);
        final String[] lines = source.split("\\R", -1);
        final List<ClassSample> result = new ArrayList<>();
        for (TypeDeclaration<?> type : cu.get().findAll(TypeDeclaration.class))
        {
            final List<MethodSample> methods = type.getMembers().stream()
                    .filter(BodyDeclaration::isMethodDeclaration)
                    .map(BodyDeclaration::asMethodDeclaration)
                    .filter(m -> !m.isPrivate() && m.getBody().isPresent())
                    .map(m -> toSample(m, type, path, lines))
                    .toList();
            if (!methods.isEmpty())
            {
                result.add(new ClassSample(
                        type.getNameAsString(),
                        type.getFullyQualifiedName().orElse(type.getNameAsString()),
                        description(type).orElse(null),
                        path,
                        methods));
            }
        }
        return result;
    }

    private static JavaParser parser()
    {
        return new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                .setAttributeComments(true));
    }

    private MethodSample toSample(MethodDeclaration method, TypeDeclaration<?> type, String path, String[] lines)
    {
        final Node first = method.getComment().map(Node.class::cast).orElse(method);
        final SourceRange range = new SourceRange(
                position(first.getBegin().orElseThrow()),
                position(method.getEnd().orElseThrow()));
        return new MethodSample(
                method.getNameAsString(),
                description(method).orElse(null),
                body(method.getBody().orElseThrow(), lines),
                type.getNameAsString(),
                path,
                range);
    }

    private static SourcePosition position(com.github.javaparser.Position position)
    {
        return new SourcePosition(position.line, position.column);
    }

    /**
     * The original text between the braces of the block, exactly as written (comments and blank lines included),
     * with the common indentation removed.
     */
    static String body(BlockStmt block, String[] lines)
    {
        final var begin = block.getBegin().orElseThrow();
        final var end = block.getEnd().orElseThrow();

        final StringBuilder sb = new StringBuilder();
        for (int line = begin.line; line <= end.line; line++)
        {
            String text = lines[line - 1];
            if (line == end.line)
            {
                text = text.substring(0, end.column - 1);
            }
            if (line == begin.line)
            {
                text = text.substring(Math.min(begin.column, text.length()));
            }
            sb.append(text).append('\n');
        }
        return Text.dedent(sb.toString());
    }

    private static Optional<String> description(Node node)
    {
        return node.getComment()
                .map(JavaSampleExtractor::commentText)
                .filter(s -> !s.isBlank());
    }

    private static String commentText(Comment comment)
    {
        if (comment instanceof JavadocComment javadoc)
        {
            return javadoc.parse().getDescription().toText().strip();
        }
        if (comment.isBlockComment())
        {
            return Text.dedent(Text.stripLeadingAsterisks(comment.getContent()));
        }
        return comment.getContent().strip();
    }

    private String relative(Path file)
    {
        final Path absolute = file.toAbsolutePath().normalize();
        final Path relative = absolute.startsWith(baseDir) ? baseDir.relativize(absolute) : absolute;
        return relative.toString().replace('\\', '/');
    }
}
