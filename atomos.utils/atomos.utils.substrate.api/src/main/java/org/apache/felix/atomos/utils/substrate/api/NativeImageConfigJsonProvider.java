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
package org.apache.felix.atomos.utils.substrate.api;

import java.util.Optional;
import java.util.ServiceLoader;

import aQute.bnd.annotation.spi.ServiceConsumer;
import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;

@ServiceConsumer(value = NativeImageConfigJsonProvider.class)
public interface NativeImageConfigJsonProvider
{

    static NativeImageConfigJsonProvider newInstance()
    {

        Optional<NativeImageConfigJsonProvider> oJsonUtil = ServiceLoader.load(
            NativeImageConfigJsonProvider.class).findFirst();

        if (oJsonUtil.isPresent())
        {
            return oJsonUtil.get();
        }
        Module m = NativeImageConfigJsonProvider.class.getModule();
        ModuleLayer l=m.getLayer();
        oJsonUtil = ServiceLoader.load(l
            ,
            NativeImageConfigJsonProvider.class).findFirst();
        if (oJsonUtil.isPresent())
        {
            return oJsonUtil.get();
        }

        throw new RuntimeException(
            String.format("ServiceLoader could not find found: %s",
                NativeImageConfigJsonProvider.class.getName()));

    }

    String json(DynamicProxyConfiguration dynamicProxyConfig) throws Exception;

    String json(ReflectionConfiguration reflectConfig) throws Exception;

    String json(ResourceConfiguration resourceConfig) throws Exception;
}
