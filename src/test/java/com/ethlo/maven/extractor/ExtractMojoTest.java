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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractMojoTest
{
    @TempDir
    Path baseDir;

    private ExtractMojo mojo;

    @BeforeEach
    void setUp() throws IOException
    {
        final Path samples = baseDir.resolve("src/test/java/samples");
        Files.createDirectories(samples);
        Files.writeString(samples.resolve("Samples.java"), """
                package samples;

                /** All about lists. */
                class Samples
                {
                    /** Make a list. */
                    void makeList()
                    {
                        var list = java.util.List.of(1, 2);
                    }
                }
                """);

        final Model model = new Model();
        model.setVersion("1.2.3");
        final MavenProject project = new MavenProject(model);
        project.setFile(baseDir.resolve("pom.xml").toFile());

        mojo = new ExtractMojo();
        mojo.project = project;
        mojo.template = TemplateRenderer.DEFAULT_TEMPLATE;
        mojo.encoding = "UTF-8";
        mojo.outputDirectory = baseDir.resolve("target/source-extractor").toFile();
        mojo.sources = List.of(new Source("src/test/java/samples"));
    }

    @Test
    void rendersToAPropertyAndAFileWithTheDefaultTemplate() throws Exception
    {
        mojo.execute();

        final String rendered = mojo.project.getProperties().getProperty("src/test/java/samples");
        assertThat(rendered).isEqualTo("""
                All about lists.

                #### makeList <sup>[source](src/test/java/samples/Samples.java#L6-L10)</sup>

                Make a list.

                ```java
                var list = java.util.List.of(1, 2);
                ```

                """);
        assertThat(baseDir.resolve("target/source-extractor/src-test-java-samples.md")).hasContent(rendered);
    }

    @Test
    void usesTheConfiguredNameAndPerSourceTemplate() throws Exception
    {
        Files.writeString(baseDir.resolve("list.txt"),
                "{{ project.version }}:{% for m in methods %}{{ m.className }}.{{ m.name }}@{{ m.range.begin.line }}{% endfor %}");
        final Source source = new Source("src/test/java/samples");
        source.setName("samples");
        source.setTemplate("list.txt");
        mojo.sources = List.of(source);

        mojo.execute();

        assertThat(mojo.project.getProperties().getProperty("samples")).isEqualTo("1.2.3:Samples.makeList@6");
        assertThat(baseDir.resolve("target/source-extractor/samples.txt")).hasContent("1.2.3:Samples.makeList@6");
    }

    @Test
    void supportsTemplatesWrittenFor03() throws Exception
    {
        Files.writeString(baseDir.resolve("legacy.md"), """
                {{class.description}}
                {% for method in methods %}[{{method.name}}]({{class.path}}/{{class.name}}.java#L{{method.range.begin.line}}C{{method.range.begin.column}}-L{{method.range.end.line}}C{{method.range.end.column + 1}}){% endfor %}""");
        mojo.template = "legacy.md";

        mojo.execute();

        assertThat(mojo.project.getProperties().getProperty("src/test/java/samples"))
                // Pebble trims the newline after a tag, exactly like 0.3 did
                .isEqualTo("All about lists.[makeList](src/test/java/samples/Samples.java#L6C5-L10C6)");
    }

    @Test
    void rendersHtmlEscapingTheCode() throws Exception
    {
        Files.writeString(baseDir.resolve("src/test/java/samples/Generic.java"),
                "class Generic { void g() { java.util.List<String> l = null; } }");
        mojo.template = "html";

        mojo.execute();

        assertThat(mojo.project.getProperties().getProperty("src/test/java/samples"))
                .contains("<pre><code class=\"language-java\">java.util.List&lt;String&gt; l = null;</code></pre>")
                .contains("<h4>makeList <small><a href=\"src/test/java/samples/Samples.java#L6-L10\">source</a></small></h4>");
        assertThat(baseDir.resolve("target/source-extractor/src-test-java-samples.html")).exists();
    }

    @Test
    void failsClearlyOnAMissingDirectory()
    {
        mojo.sources = List.of(new Source("src/nope"));

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("does not exist");
    }

    @Test
    void failsClearlyOnAMissingTemplate()
    {
        mojo.template = "missing.md";

        assertThatThrownBy(mojo::execute).hasMessageContaining("Template 'missing.md' not found");
    }

    @Test
    void canFailOnEmptySources() throws IOException
    {
        Files.createDirectories(baseDir.resolve("src/empty"));
        mojo.sources = List.of(new Source("src/empty"));
        mojo.failOnEmpty = true;

        assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class).hasMessageContaining("No samples found");
    }

    @Test
    void skips() throws Exception
    {
        mojo.skip = true;
        mojo.sources = List.of(new Source("src/nope"));

        mojo.execute();

        assertThat(new File(mojo.outputDirectory, "src-nope.md")).doesNotExist();
    }

    @Test
    void makesSafeFileNames()
    {
        assertThat(ExtractMojo.fileName("src/test/java/samples")).isEqualTo("src-test-java-samples");
        assertThat(ExtractMojo.fileName("/abs/path/")).isEqualTo("abs-path");
        assertThat(ExtractMojo.fileName("samples.v2")).isEqualTo("samples.v2");
    }
}
