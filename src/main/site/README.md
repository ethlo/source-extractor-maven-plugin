### source-extractor-maven-plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.ethlo.documentation/source-extractor-maven-plugin.svg)](https://central.sonatype.com/artifact/com.ethlo.documentation/source-extractor-maven-plugin)
[![License](https://img.shields.io/github/license/ethlo/source-extractor-maven-plugin)](LICENSE)

Keep the code samples in your README compiling, tested and up to date, by writing them as tests.

This plugin reads methods from Java source files, typically a package of tests, and renders them as Markdown (or
HTML, or anything a [Pebble](https://pebbletemplates.io) template can produce). The result goes into a Maven property
that you can drop into any filtered resource, and into a file under `target/` for tools that include files.

An example can be seen here: https://github.com/ethlo/itu/?tab=readme-ov-file#parsing

Standing on the shoulders of these awesome projects:
* https://github.com/javaparser/javaparser
* https://github.com/PebbleTemplates/pebble

### Quick start

**1. Write your samples as tests**, for example in `src/test/java/samples/ListSamples.java`. Each method that is
not `private` becomes a sample. Its comment becomes the description, and its body, exactly as written, becomes the
code.

```java
class ListSamples
{
    /**
     * Lists are immutable when created with List.of.
     */
    @Test
    void immutableList()
    {
        final List<String> list = List.of("a", "b");

        assertThrows(UnsupportedOperationException.class, () -> list.add("c"));
    }
}
```

**2. Add the plugin** and tell it where the samples are:

```xml
<plugin>
    <groupId>com.ethlo.documentation</groupId>
    <artifactId>source-extractor-maven-plugin</artifactId>
    <version>${project.version}</version>
    <configuration>
        <sources>
            <source>
                <directory>src/test/java/samples</directory>
                <name>samples</name>
            </source>
        </sources>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>extract</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

**3. Use the result** in a filtered resource. Put your README template in `src/main/site/README.md`, write
`\${samples}` where the samples should go, and let the resources plugin render it to the project root:

```xml
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
        </resource>
        <resource>
            <directory>src/main/site</directory>
            <filtering>true</filtering>
            <includes>
                <include>README.md</include>
            </includes>
            <targetPath>\${project.basedir}</targetPath>
        </resource>
    </resources>
</build>
```

Run `mvn process-resources` (or any later phase) and `README.md` is up to date. The `extract` goal binds to
`generate-resources` by default, so it always runs before resource filtering.

Tip: set `<escapeString>\</escapeString>` on the `maven-resources-plugin` if your README needs a literal `\${...}`.

### Configuration

| Parameter         | Default                                     | User property                     | Description                                                                                       |
|-------------------|---------------------------------------------|-----------------------------------|---------------------------------------------------------------------------------------------------|
| `sources`         | (required)                                  |                                   | The directories to extract samples from. See below.                                               |
| `template`        | `markdown`                                  | `source-extractor.template`       | `markdown`, `html`, a file relative to the project, or a classpath resource.                      |
| `outputDirectory` | `\${project.build.directory}/source-extractor` | `source-extractor.outputDirectory` | Where each rendered source is also written, as `<name>.<template extension>`.                     |
| `encoding`        | `\${project.build.sourceEncoding}`           | `source-extractor.encoding`       | Encoding of the sources and templates. Falls back to UTF-8.                                       |
| `failOnEmpty`     | `false`                                     | `source-extractor.failOnEmpty`    | Fail the build when a source has no samples, instead of warning.                                  |
| `skip`            | `false`                                     | `source-extractor.skip`           | Skip the plugin.                                                                                  |

Each `<source>` is either just a directory, or a directory with a name and its own template:

```xml
<sources>
    <!-- Available as \${src/test/java/samples} -->
    <source>src/test/java/samples</source>

    <!-- Available as \${parsing}, rendered with a template of its own -->
    <source>
        <directory>src/test/java/samples/parsing</directory>
        <name>parsing</name>
        <template>src/main/site/parsing.md</template>
    </source>
</sources>
```

In a project that uses the plugin, `mvn source-extractor:help -Ddetail` prints the same reference.

### What counts as a sample

* Every method with a body that is not `private`, in every class (nested classes included) under the directory.
  Private methods are left out, so you can keep helpers next to your samples.
* The body is taken from the file exactly as written, comments and blank lines included, with the common indentation
  removed.
* The description is the method's Javadoc, block comment or line comment, cleaned of `*` decoration. Javadoc tags
  such as `@see` are left out.
* Files are read in path order and methods in source order, so the output is stable between builds.

### Templates

The built-in `markdown` template renders each class description, then each sample as a heading with a link to its
source, the description, and a fenced code block. The `html` template renders the same as `<section>` and
`<article>` elements, ready for your own CSS.

To write your own, point `template` at a [Pebble](https://pebbletemplates.io/wiki/guide/basic-usage/) file. These
variables are available:

| Variable  | Contents                                                                                                                              |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------|
| `classes` | Each class with samples: `name`, `qualifiedName`, `description`, `path` and `methods`.                                              |
| `methods` | Every sample across all classes: `name`, `description`, `body`, `className`, `path`, `range` and `link`.                           |
| `source`  | The configured source: `directory` and `name`.                                                                                       |
| `project` | The Maven project, for example `project.version`.                                                                                    |

`path` is relative to the project base directory, and `link` is `path` with a line anchor, like
`src/test/java/samples/ListSamples.java#L5-L14`, which works as a relative link on GitHub and GitLab. `range` has
`begin` and `end`, each with a 1-based `line` and `column`.

A minimal template that lists the samples:

```twig
{% for method in methods %}
* [{{ method.className }}.{{ method.name }}]({{ method.link }}): {{ method.description }}
{% endfor %}
```

Pebble escapes HTML by default. Use `| raw` for text that should be used as is, like code in a Markdown fence.

### Upgrading from 0.3

* Java 17 and Maven 3.9 or newer are required.
* The default phase is now `generate-resources`, so you can remove `<phase>initialize</phase>` from the execution.
* `template` is now optional. Templates written for 0.3 still work: the `class` and `methods` variables are still
  there, and `class.path` is still the source directory.
* Samples now keep blank lines and comments from the body, and get correct indentation for nested blocks.
* `private` methods are no longer extracted.
* Methods are grouped under their own class. Before, every method was attributed to the first class found.
* The `skip` user property is now `source-extractor.skip`.

### Extracted samples

${src/test/java/samples}
