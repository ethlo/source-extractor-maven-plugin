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
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.loader.Loader;
import io.pebbletemplates.pebble.template.PebbleTemplate;

/**
 * Renders samples with a <a href="https://pebbletemplates.io">Pebble</a> template.
 *
 * <p>A template is either the name of a built-in template ({@value #DEFAULT_TEMPLATE} or {@code html}), a path to a
 * file (relative to the project base directory, or absolute), or a resource on the plugin classpath.</p>
 */
public final class TemplateRenderer
{
    /**
     * The template used when none is configured.
     */
    public static final String DEFAULT_TEMPLATE = "markdown";

    private static final Map<String, String> BUILT_IN = Map.of(
            "markdown", "com/ethlo/maven/extractor/templates/markdown.md",
            "html", "com/ethlo/maven/extractor/templates/html.html");

    private final Path baseDir;
    private final Charset encoding;

    public TemplateRenderer(Path baseDir, Charset encoding)
    {
        this.baseDir = baseDir;
        this.encoding = encoding;
    }

    /**
     * Render a model with a template.
     *
     * @param template the template name, file or classpath resource; {@code null} means {@value #DEFAULT_TEMPLATE}
     * @param model    the variables available in the template
     * @return the rendered text
     * @throws IOException if the template cannot be found or read
     */
    public String render(String template, Map<String, Object> model) throws IOException
    {
        final StringWriter out = new StringWriter();
        compile(template == null || template.isBlank() ? DEFAULT_TEMPLATE : template.strip()).evaluate(out, model, Locale.ROOT);
        return out.toString();
    }

    /**
     * The file extension of the template, used for the output file. Built-in templates map to {@code md} and
     * {@code html}.
     *
     * @param template the template, as passed to {@link #render(String, Map)}
     * @return the extension, without the dot
     */
    public static String extension(String template)
    {
        final String name = template == null || template.isBlank()
                ? BUILT_IN.get(DEFAULT_TEMPLATE)
                : BUILT_IN.getOrDefault(template.strip(), template.strip());
        final String fileName = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        final int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1) : "txt";
    }

    private PebbleTemplate compile(String template) throws IOException
    {
        final String builtIn = BUILT_IN.get(template);
        if (builtIn != null)
        {
            return classpathEngine().getTemplate(builtIn);
        }

        final Path file = baseDir.resolve(template);
        if (Files.isRegularFile(file))
        {
            // Load from the template's own directory, so {% include %} and {% extends %} work relative to it
            final FileLoader loader = new FileLoader(file.toAbsolutePath().getParent().toString());
            loader.setCharset(encoding.name());
            return engine(loader).getTemplate(file.getFileName().toString());
        }

        final String resource = template.startsWith("/") ? template.substring(1) : template;
        if (getClass().getClassLoader().getResource(resource) != null)
        {
            return classpathEngine().getTemplate(resource);
        }

        throw new IOException("Template '" + template + "' not found. Use one of the built-in templates "
                + new TreeSet<>(BUILT_IN.keySet()) + ", a file (looked for " + file
                + "), or a classpath resource.");
    }

    private PebbleEngine classpathEngine()
    {
        final ClasspathLoader loader = new ClasspathLoader(getClass().getClassLoader());
        loader.setCharset(encoding.name());
        return engine(loader);
    }

    private static PebbleEngine engine(Loader<?> loader)
    {
        return new PebbleEngine.Builder()
                .loader(loader)
                .strictVariables(false)
                .newLineTrimming(true)
                .cacheActive(false)
                .build();
    }
}
