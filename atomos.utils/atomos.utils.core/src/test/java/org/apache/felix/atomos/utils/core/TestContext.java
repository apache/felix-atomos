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
package org.apache.felix.atomos.utils.core;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.api.RegisterServiceCall;
import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;

public interface TestContext extends Context
{
    @Override
    default ReflectionConfiguration getReflectConfig()
    {

        return null;
    }

    @Override
    default void addReflectionClass(String className)
    {

    }

    @Override
    default void addReflectionConstructor(String bundleActivatorClassName,
        String[] parameterTypeNames)
    {

    }

    @Override
    default void addReflectionConstructorAllPublic(String clazzName)
    {

    }

    @Override
    default void addReflectionField(String fieldName, Class<?> clazz)
    {

    }

    @Override
    default void addReflectionFieldsAllPublic(String className)
    {

    }

    @Override
    default void addFile(Path path, FileType type)
    {

    }

    @Override
    default void addReflecionMethod(String methodName, Class<?>[] parameterTypes, Class<?> clazz)
    {

    }

    @Override
    default void addReflectionMethodsAllPublic(String clazz)
    {

    }

    @Override
    default void addRegisterServiceCalls(RegisterServiceCall registerServiceCall)
    {

    }

    @Override
    default void addResourceConfig(ResourceConfiguration resourceConfig)
    {
        // TODO Auto-generated method stub

    }

    @Override
    default Stream<Path> getFiles(FileType... fileType)
    {
        return null;
    }

    @Override
    default List<RegisterServiceCall> getRegisterServiceCalls()
    {
        return null;
    }

    @Override
    default ResourceConfiguration getResourceConfig()
    {
        return null;
    }

    @Override
    default void addDynamicProxyConfigs(String... items)
    {

    }

    @Override
    default DynamicProxyConfiguration getDynamicProxyConfig()
    {
        return null;
    }
}
