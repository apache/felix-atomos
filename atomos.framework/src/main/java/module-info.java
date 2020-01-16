
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
import org.atomos.framework.base.AtomosFrameworkUtilHelper;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.connect.FrameworkUtilHelper;

open module atomos.framework
{
    exports org.atomos.framework;

    requires transitive org.eclipse.osgi;
    requires static osgi.annotation;
    requires static jdk.unsupported;
    requires static org.apache.felix.gogo.runtime;

    uses ConnectFrameworkFactory;

    provides FrameworkUtilHelper with AtomosFrameworkUtilHelper;
}
