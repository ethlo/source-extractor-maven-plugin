package com.ethlo.maven.extractor.model;

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
 * A single extracted sample: one method, its comment and its body.
 *
 * @param name        the method name
 * @param description the cleaned-up method comment, or {@code null} if it has none
 * @param body        the method body, without the surrounding braces, re-indented to column 0
 * @param className   the simple name of the class declaring the method
 * @param path        the path of the source file relative to the project base directory, always using {@code /}
 * @param range       the range of the method in the source file, including its comment
 */
public record MethodSample(String name, String description, String body, String className, String path, SourceRange range)
{
    /**
     * A relative link to the method in its source file, like {@code src/test/java/samples/Foo.java#L12-L20}.
     *
     * @return the link
     */
    public String link()
    {
        return path + "#" + range.anchor();
    }
}
