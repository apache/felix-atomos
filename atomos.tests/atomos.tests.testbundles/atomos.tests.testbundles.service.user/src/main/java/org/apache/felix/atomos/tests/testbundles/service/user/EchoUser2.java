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
package org.apache.felix.atomos.tests.testbundles.service.user;

import java.util.Map;

import org.apache.felix.atomos.tests.testbundles.service.contract.Echo;
import org.osgi.annotation.bundle.Requirement;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = EchoUser2.class, property = {
"echo.reference:Boolean=true" }, immediate = true)
@Requirement(namespace = "osgi.ee", filter = "(&(osgi.ee=JavaSE)(version=1.8))")
public class EchoUser2
{
    @Activate
    public EchoUser2(Map<String, Object> componentProps, @Reference Echo echo)
    {
        System.out.println("Activated via constructor: " + getClass().getName());
    }


}
