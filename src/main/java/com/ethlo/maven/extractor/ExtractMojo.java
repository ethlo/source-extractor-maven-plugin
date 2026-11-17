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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.ethlo.maven.extractor.model.ClassSample;
import com.ethlo.maven.extractor.model.MethodSample;

/**
 * Extracts methods from Java source files and renders them with a template, so code samples in your documentation
 * are the code you actually compile and test.
 *
 * <p>For every configured source the rendered result is</p>
 * <ul>
 *     <li>set as a Maven project property named after the source, ready for resource filtering, and</li>
 *     <li>written to {@code outputDirectory}, for tools that include files instead.</li>
 * </ul>
 *
 * <p>Templates can use these variables:</p>
 * <ul>
 *     <li>{@code classes}: every class with samples, each with {@code name}, {@code qualifiedName},
 *     {@code description}, {@code path} and {@code methods}</li>
 *     <li>{@code methods}: every sample across all classes, each with {@code name}, {@code description},
 *     {@code body}, {@code className}, {@code path}, {@code range} and {@code link}</li>
 *     <li>{@code source}: the configured source, with {@code directory} and {@code name}</li>
 *     <li>{@code project}: the Maven project, for example {@code project.version}</li>
 *     <li>{@code class}: the first class, kept for templates written for 0.3 and earlier</li>
 * </ul>
 */
@Mojo(name = "extract", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class ExtractMojo extends AbstractMojo
{
    /**
     * The directories to extract samples from. Each entry is either a directory,
     * {@code <source>src/test/java/samples</source>}, or a {@code <source>} with {@code <directory>},
     * {@code <name>} and {@code <template>}.
     */
    @Parameter(required = true)
    List<Source> sources;

    /**
     * The template used to render each source: {@code markdown} (the default), {@code html}, a file relative to the
     * project base directory, or a classpath resource. A {@code <template>} on a source overrides it.
     */
    @Parameter(property = "source-extractor.template", defaultValue = TemplateRenderer.DEFAULT_TEMPLATE)
    String template;

    /**
     * Where the rendered results are written, one file per source.
     */
    @Parameter(property = "source-extractor.outputDirectory", defaultValue = "${project.build.directory}/source-extractor")
    File outputDirectory;

    /**
     * The encoding of the source files and templates.
     */
    @Parameter(property = "source-extractor.encoding", defaultValue = "${project.build.sourceEncoding}")
    String encoding;

    /**
     * Fail the build if a source contains no samples, instead of just warning.
     */
    @Parameter(property = "source-extractor.failOnEmpty", defaultValue = "false")
    boolean failOnEmpty;

    /**
     * Skip the plugin.
     */
    @Parameter(property = "source-extractor.skip", defaultValue = "false")
    boolean skip;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        if (skip)
        {
            getLog().info("Skipping source extraction");
            return;
        }

        final Path baseDir = project.getBasedir().toPath();
        final Charset charset = charset();
        final JavaSampleExtractor extractor = new JavaSampleExtractor(baseDir, charset, getLog()::warn);
        final TemplateRenderer renderer = new TemplateRenderer(baseDir, charset);

        checkUnique();
        for (Source source : sources)
        {
            process(source, baseDir, extractor, renderer, charset);
        }
    }

    private void process(Source source, Path baseDir, JavaSampleExtractor extractor, TemplateRenderer renderer, Charset charset)
            throws MojoExecutionException, MojoFailureException
    {
        final Path directory = baseDir.resolve(source.getDirectory());
        if (!Files.isDirectory(directory))
        {
            throw new MojoFailureException("Source directory " + directory + " does not exist");
        }

        final List<ClassSample> classes;
        try
        {
            classes = extractor.extract(directory);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Could not read " + directory, e);
        }

        final List<MethodSample> methods = classes.stream().flatMap(c -> c.methods().stream()).toList();
        if (methods.isEmpty())
        {
            final String message = "No samples found in " + source.getDirectory();
            if (failOnEmpty)
            {
                throw new MojoFailureException(message);
            }
            getLog().warn(message);
        }

        final String sourceTemplate = templateFor(source);
        final String rendered;
        try
        {
            rendered = renderer.render(sourceTemplate, model(source, classes, methods));
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Could not render " + source.getDirectory() + ": " + e.getMessage(), e);
        }

        project.getProperties().setProperty(source.getName(), rendered);

        final Path output = outputFile(source);
        try
        {
            Files.createDirectories(output.getParent());
            Files.writeString(output, rendered, charset);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Could not write " + output, e);
        }

        getLog().info("Extracted " + methods.size() + " sample(s) from " + classes.size() + " class(es) in "
                + source.getDirectory() + " to ${" + source.getName() + "} and " + baseDir.relativize(output.toAbsolutePath()));
        if (getLog().isDebugEnabled())
        {
            getLog().debug("Rendered " + source.getName() + ":\n" + rendered);
        }
    }

    /**
     * Two sources with the same name would overwrite each other's property, and two names that map to the same file
     * name (like {@code samples/a} and {@code samples-a}) would overwrite each other's output file. Fail before
     * writing anything rather than silently losing one of them.
     */
    private void checkUnique() throws MojoFailureException
    {
        final Map<String, Source> byName = new HashMap<>();
        final Map<Path, Source> byFile = new HashMap<>();
        for (Source source : sources)
        {
            if (source.getDirectory() == null || source.getDirectory().isBlank())
            {
                throw new MojoFailureException("Every <source> needs a directory");
            }
            final Source sameName = byName.putIfAbsent(source.getName(), source);
            if (sameName != null)
            {
                throw new MojoFailureException("Sources '" + sameName + "' and '" + source + "' are both named '"
                        + source.getName() + "'. Give one of them a different <name>.");
            }
            final Source sameFile = byFile.putIfAbsent(outputFile(source), source);
            if (sameFile != null)
            {
                throw new MojoFailureException("Sources named '" + sameFile.getName() + "' and '" + source.getName()
                        + "' would both be written to " + outputFile(source) + ". Give one of them a different <name>.");
            }
        }
    }

    private String templateFor(Source source)
    {
        return source.getTemplate() != null ? source.getTemplate() : template;
    }

    private Path outputFile(Source source)
    {
        return outputDirectory.toPath()
                .resolve(fileName(source.getName()) + "." + TemplateRenderer.extension(templateFor(source)))
                .toAbsolutePath()
                .normalize();
    }

    private Map<String, Object> model(Source source, List<ClassSample> classes, List<MethodSample> methods)
    {
        final Map<String, Object> model = new LinkedHashMap<>();
        model.put("classes", classes);
        model.put("methods", methods);
        model.put("source", source);
        model.put("project", project);
        if (!classes.isEmpty())
        {
            // The 0.3 model: the first class, with "path" being the source directory
            final ClassSample first = classes.get(0);
            final Map<String, Object> legacy = new LinkedHashMap<>();
            legacy.put("name", first.name());
            legacy.put("description", first.description());
            legacy.put("path", source.getDirectory());
            model.put("class", legacy);
        }
        return model;
    }

    private Charset charset()
    {
        return encoding == null || encoding.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    static String fileName(String name)
    {
        return name.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    }
}
