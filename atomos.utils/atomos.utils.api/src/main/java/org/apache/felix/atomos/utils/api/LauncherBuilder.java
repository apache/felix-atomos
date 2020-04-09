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
package org.apache.felix.atomos.utils.api;

import java.util.Map;

import org.apache.felix.atomos.utils.api.plugin.SubstratePlugin;

public interface LauncherBuilder
{

    LauncherBuilder addPlugin(Class<? extends SubstratePlugin<?>> pluginClasses,
        Map<String, Object> cfgMap);

    <C> LauncherBuilder addPlugin(Class<? extends SubstratePlugin<C>> pluginClasses,
        C cfg);

    LauncherBuilder addPlugin(String pluginClassNames, Map<String, Object> cfgMap);

    <C> LauncherBuilder addPlugin(SubstratePlugin<C> pluginClasses, C cfg);

    Launcher build();

}
