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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Small text helpers for cleaning up extracted source.
 */
final class Text
{
    private Text()
    {
    }

    /**
     * Remove leading and trailing blank lines, trailing whitespace on each line, and the indentation that all
     * non-blank lines have in common. Relative indentation is kept.
     */
    static String dedent(String text)
    {
        final List<String> lines = Arrays.stream(text.split("\\R", -1))
                .map(String::stripTrailing)
                .collect(Collectors.toList());

        while (!lines.isEmpty() && lines.get(0).isEmpty())
        {
            lines.remove(0);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty())
        {
            lines.remove(lines.size() - 1);
        }

        final int indent = lines.stream()
                .filter(l -> !l.isEmpty())
                .mapToInt(Text::indentation)
                .min()
                .orElse(0);

        return lines.stream()
                .map(l -> l.isEmpty() ? l : l.substring(indent))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Remove the decorative {@code *} that block comments often have at the start of each line.
     */
    static String stripLeadingAsterisks(String comment)
    {
        return Arrays.stream(comment.split("\\R", -1))
                .map(l -> l.replaceFirst("^\\s*\\* ?", ""))
                .collect(Collectors.joining("\n"));
    }

    private static int indentation(String line)
    {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i)))
        {
            i++;
        }
        return i;
    }
}
