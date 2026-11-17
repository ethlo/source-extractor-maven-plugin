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

/**
 * One directory of samples to extract, as configured in the {@code <sources>} list.
 *
 * <p>The short form is just the directory:</p>
 * <pre>{@code <source>src/test/java/samples</source>}</pre>
 *
 * <p>The long form lets you name the result and pick a template for this source only:</p>
 * <pre>{@code
 * <source>
 *     <directory>src/test/java/samples</directory>
 *     <name>samples</name>
 *     <template>html</template>
 * </source>
 * }</pre>
 */
public class Source
{
    /**
     * The directory to scan for {@code .java} files, relative to the project base directory. Required.
     */
    private String directory;

    /**
     * The name of the Maven property that receives the rendered result, and the base name of the output file.
     * Defaults to the directory, so {@code src/test/java/samples} is available as
     * {@code ${src/test/java/samples}}.
     */
    private String name;

    /**
     * A template for this source only. Overrides the plugin-level {@code <template>}.
     */
    private String template;

    public Source()
    {
    }

    public Source(String directory)
    {
        this.directory = directory;
    }

    /**
     * Called by Maven for the short form, {@code <source>dir</source>}.
     *
     * @param directory the directory to scan
     */
    public void set(String directory)
    {
        this.directory = directory;
    }

    public String getDirectory()
    {
        return directory;
    }

    public void setDirectory(String directory)
    {
        this.directory = directory;
    }

    public String getName()
    {
        return name != null && !name.isBlank() ? name : directory;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getTemplate()
    {
        return template;
    }

    public void setTemplate(String template)
    {
        this.template = template;
    }

    @Override
    public String toString()
    {
        return directory;
    }
}
