package samples;

/*-
 * #%L
 * java-code-extractor-maven-plugin
 * %%
 * Copyright (C) 2024 Morten Haraldsen (ethlo)
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

public class SampleCode
{
    /*
     <p>Description of the cool feature A goes here!</p>
     */
    void showOffFeatureA()
    {
        final List<String> list = Arrays.asList("something", "cool");

        assert list.size() == 2;
        assert list != null;
    }

    /*
     Description of the cool feature B goes here!

     And some more here!
     */
    void showOffFeatureB()
    {
        final List<String> list = Arrays.asList("something", "else", "cool");

        assert list.size() == 2;
        assert list != null;
    }
}
