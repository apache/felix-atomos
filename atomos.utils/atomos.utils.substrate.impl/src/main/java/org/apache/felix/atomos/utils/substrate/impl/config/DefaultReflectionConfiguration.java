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
package org.apache.felix.atomos.utils.substrate.impl.config;

import java.util.List;

import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionClassConfig;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;

public class DefaultReflectionConfiguration implements ReflectionConfiguration
{

    private final List<ReflectionClassConfig> reflectClassConfigs;

    public DefaultReflectionConfiguration(final List<ReflectionClassConfig> reflectClassConfigs)
    {
        this.reflectClassConfigs = reflectClassConfigs;
    }

    @Override
    public List<ReflectionClassConfig> getClassConfigs()
    {
        return reflectClassConfigs;
    }

}
