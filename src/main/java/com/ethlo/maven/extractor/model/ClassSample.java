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

import java.util.List;

/**
 * A class containing samples.
 *
 * @param name          the simple class name
 * @param qualifiedName the fully qualified class name
 * @param description   the cleaned-up class comment, or {@code null} if it has none
 * @param path          the path of the source file relative to the project base directory, always using {@code /}
 * @param methods       the samples declared in this class, in source order
 */
public record ClassSample(String name, String qualifiedName, String description, String path, List<MethodSample> methods)
{
    public ClassSample
    {
        methods = List.copyOf(methods);
    }
}
