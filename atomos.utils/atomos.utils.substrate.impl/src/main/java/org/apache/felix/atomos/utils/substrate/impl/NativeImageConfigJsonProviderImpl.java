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
package org.apache.felix.atomos.utils.substrate.impl;

import aQute.bnd.annotation.spi.ServiceProvider;
import org.apache.felix.atomos.utils.substrate.api.NativeImageConfigJsonProvider;
import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.json.DynamicProxyJsonUtil;
import org.apache.felix.atomos.utils.substrate.impl.json.ReflectJsonUtil;
import org.apache.felix.atomos.utils.substrate.impl.json.ResourceJsonUtil;

@ServiceProvider(NativeImageConfigJsonProvider.class)
public class NativeImageConfigJsonProviderImpl implements NativeImageConfigJsonProvider
{

    @Override
    public String json(final DynamicProxyConfiguration dynamicProxyConfig)
        throws Exception
    {
        return DynamicProxyJsonUtil.json(dynamicProxyConfig);
    }

    @Override
    public String json(final ReflectionConfiguration reflectConfig) throws Exception
    {
        return ReflectJsonUtil.json(reflectConfig);
    }

    @Override
    public String json(final ResourceConfiguration resourceConfig) throws Exception
    {

        return ResourceJsonUtil.json(resourceConfig);
    }

}
