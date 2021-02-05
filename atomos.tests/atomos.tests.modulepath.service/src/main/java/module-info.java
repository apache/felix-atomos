
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
import org.apache.felix.atomos.Atomos;

module org.apache.felix.atomos.tests.modulepath.service
{
    requires org.apache.felix.atomos;
    requires org.apache.felix.atomos.tests.testbundles.service.contract;
    requires org.apache.felix.atomos.tests.testbundles.service.impl;
    requires org.apache.felix.atomos.tests.testbundles.service.impl.activator;
    requires org.apache.felix.scr;
    requires osgi.promise;

    uses Atomos;

    opens org.apache.felix.atomos.tests.modulepath.service;

}
