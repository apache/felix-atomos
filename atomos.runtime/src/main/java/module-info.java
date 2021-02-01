
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
import org.apache.felix.atomos.impl.runtime.base.AtomosFrameworkUtilHelper;
import org.apache.felix.atomos.impl.runtime.base.AtomosModuleConnector;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.connect.FrameworkUtilHelper;
import org.osgi.framework.connect.ModuleConnector;

open module org.apache.felix.atomos.runtime
{
    exports org.apache.felix.atomos.runtime;
    exports org.apache.felix.atomos.launch;

    requires transitive osgi.core;
    requires static osgi.annotation;
    requires static jdk.unsupported;
    requires static org.apache.felix.gogo.runtime;

    uses ConnectFrameworkFactory;
    uses ModuleConnector;

    provides FrameworkUtilHelper with AtomosFrameworkUtilHelper;
    provides ModuleConnector with AtomosModuleConnector;
}
