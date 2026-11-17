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
 * An inclusive range in a source file.
 *
 * @param begin the first position of the range
 * @param end   the last position of the range (inclusive)
 */
public record SourceRange(SourcePosition begin, SourcePosition end)
{
    /**
     * A GitHub/GitLab style line anchor, like {@code L12-L20}.
     *
     * @return the anchor, without the leading {@code #}
     */
    public String anchor()
    {
        return "L" + begin.line() + "-L" + end.line();
    }
}
