/*
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
 */

package org.apache.felix.atomos.tests.testbundles.reflect.command;

import org.osgi.service.component.annotations.Component;

@Component(immediate = true, service = CmdExample.class)
@CommandScope("reflect-test")
@CommandFunction(value = { "a", "single", "multiple" })
public class CmdExample extends AbstractCmd
{

    public String single()
    {
        return "single";
    }

    public String multiple(String s)
    {
        return "multiple" + s;
    }

    public String multiple(String s, boolean b)
    {
        return "multiple" + s + b;
    }

    public A a()
    {
        A a = new A();
        a.a = "a";
        return a;
    }
}
