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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ethlo.maven.extractor.model.ClassSample;
import com.ethlo.maven.extractor.model.MethodSample;
import com.ethlo.maven.extractor.model.SourcePosition;

class JavaSampleExtractorTest
{
    @TempDir
    Path baseDir;

    private final List<String> warnings = new ArrayList<>();

    @Test
    void extractsTheSamplesInThisRepository() throws IOException
    {
        final Path projectDir = Path.of("").toAbsolutePath();
        final List<ClassSample> classes = new JavaSampleExtractor(projectDir, StandardCharsets.UTF_8, warnings::add)
                .extract(projectDir.resolve("src/test/java/samples"));

        assertThat(classes).singleElement().satisfies(c -> {
            assertThat(c.name()).isEqualTo("SampleCode");
            assertThat(c.qualifiedName()).isEqualTo("samples.SampleCode");
            assertThat(c.path()).isEqualTo("src/test/java/samples/SampleCode.java");
            assertThat(c.methods()).extracting(MethodSample::name).containsExactly("showOffFeatureA", "showOffFeatureB");
        });
        assertThat(warnings).isEmpty();
    }

    @Test
    void keepsTheBodyExactlyAsWrittenButDedented() throws IOException
    {
        write("demo/Demo.java", """
                package demo;

                class Demo
                {
                    void sample()
                    {
                        int a = 1;

                        // explain the next step
                        if (a > 0)
                        {
                            a++;
                        }
                    }
                }
                """);

        final MethodSample sample = extract().get(0).methods().get(0);

        assertThat(sample.body()).isEqualTo("""
                int a = 1;

                // explain the next step
                if (a > 0)
                {
                    a++;
                }""");
    }

    @Test
    void handlesOneLineBodies() throws IOException
    {
        write("Demo.java", """
                class Demo {
                    int sample() { return 42; }
                    void empty() {}
                }
                """);

        assertThat(extract().get(0).methods()).extracting(MethodSample::body).containsExactly("return 42;", "");
    }

    @Test
    void cleansUpJavadocBlockAndLineComments() throws IOException
    {
        write("Demo.java", """
                /**
                 * The class.
                 *
                 * With two paragraphs.
                 */
                class Demo
                {
                    /**
                     * A javadoc description.
                     *
                     * @see Object
                     */
                    void javadoc() {}

                    /*
                     * A block comment
                     *   with indentation.
                     */
                    void block() {}

                    // A line comment
                    void line() {}

                    void none() {}
                }
                """);

        final ClassSample demo = extract().get(0);
        assertThat(demo.description()).isEqualTo("The class.\n\nWith two paragraphs.");
        assertThat(demo.methods()).extracting(MethodSample::description).containsExactly(
                "A javadoc description.",
                "A block comment\n  with indentation.",
                "A line comment",
                null);
    }

    @Test
    void reportsRangesIncludingTheComment() throws IOException
    {
        write("Demo.java", """
                class Demo
                {
                    /** Doc */
                    void sample()
                    {
                    }
                }
                """);

        final MethodSample sample = extract().get(0).methods().get(0);
        assertThat(sample.range().begin()).isEqualTo(new SourcePosition(3, 5));
        assertThat(sample.range().end()).isEqualTo(new SourcePosition(6, 5));
        assertThat(sample.link()).isEqualTo("src/Demo.java#L3-L6");
    }

    @Test
    void skipsPrivateAndAbstractMethods() throws IOException
    {
        write("Demo.java", """
                abstract class Demo
                {
                    public void visible() {}
                    void packagePrivate() {}
                    protected void inherited() {}
                    private void helper() {}
                    abstract void noBody();
                }
                """);

        assertThat(extract().get(0).methods()).extracting(MethodSample::name)
                .containsExactly("visible", "packagePrivate", "inherited");
    }

    @Test
    void groupsByClassAcrossFilesAndPackagesInStableOrder() throws IOException
    {
        write("b/Second.java", "package b; class Second { void two() {} }");
        write("a/First.java", "package a; class First { void one() {} static class Nested { void inner() {} } }");
        write("c/Empty.java", "package c; class Empty { private void hidden() {} }");

        final List<ClassSample> classes = extract();

        assertThat(classes).extracting(ClassSample::qualifiedName).containsExactly("a.First", "a.First.Nested", "b.Second");
        assertThat(classes).extracting(ClassSample::path)
                .containsExactly("src/a/First.java", "src/a/First.java", "src/b/Second.java");
        assertThat(classes.get(1).methods()).extracting(MethodSample::className).containsExactly("Nested");
    }

    @Test
    void supportsModernJavaSyntax() throws IOException
    {
        write("Demo.java", """
                class Demo
                {
                    record Point(int x, int y) {}

                    String sample(Object o)
                    {
                        return switch (o) {
                            case Point(int x, int y) when x > 0 -> "right of origin";
                            case String s -> s;
                            default -> \"""
                                text block
                                \""";
                        };
                    }
                }
                """);

        assertThat(extract()).extracting(ClassSample::name).containsExactly("Demo");
        assertThat(warnings).isEmpty();
    }

    @Test
    void warnsAndContinuesOnUnparseableFiles() throws IOException
    {
        write("Broken.java", "class Broken { void x( }");
        write("Fine.java", "class Fine { void ok() {} }");

        assertThat(extract()).extracting(ClassSample::name).containsExactly("Fine");
        assertThat(warnings).singleElement().asString().startsWith("Skipping src/Broken.java");
    }

    private List<ClassSample> extract() throws IOException
    {
        return new JavaSampleExtractor(baseDir, StandardCharsets.UTF_8, warnings::add).extract(baseDir.resolve("src"));
    }

    private void write(String path, String content) throws IOException
    {
        final Path file = baseDir.resolve("src").resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
