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

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;

public interface Context
{

    default void addReflectionClass(Class<?> clazz)
    {
        addReflectionClass(clazz.getName());
    }

    void addReflectionClass(String className);

    default void addReflectionConstructor(Class<?> clazz, Class<?>[] parameterTypes)
    {
        String[] parameterTypeNames = null;
        if (parameterTypes != null)
        {
            parameterTypeNames = Stream.of(parameterTypes).map(Class::getName).toArray(
                String[]::new);
        }
        addReflectionConstructor(clazz, parameterTypeNames);
    }

    default void addReflectionConstructor(Class<?> bundleActivatorClass,
        String[] parameterTypeNames)
    {
        addReflectionConstructor(bundleActivatorClass.getName(), parameterTypeNames);
    }

    void addReflectionConstructor(String bundleActivatorClassName,
        String[] parameterTypeNames);

    default void addReflectionConstructorAllPublic(Class<?> clazz)
    {
        addReflectionConstructorAllPublic(clazz.getName());
    }

    void addReflectionConstructorAllPublic(String clazzName);

    default void addReflectionConstructorDefault(Class<?> clazz)
    {
        addReflectionConstructor(clazz, new Class[] {});

    }

    void addReflectionField(String fieldName, Class<?> clazz);

    default void addReflectionFieldsAllPublic(Class<?> clazz)
    {

        addReflectionFieldsAllPublic(clazz.getName());
    }

    void addReflectionFieldsAllPublic(String className);

    void addFile(Path path, FileType type);

    default void addReflectionMethod(Method method)
    {
        addReflecionMethod(method.getName(), method.getParameterTypes(),
            method.getDeclaringClass());
    }

    default void addReflectionMethod(String mName, Class<?> clazz)
    {
        addReflecionMethod(mName, null, clazz);
    }

    void addReflecionMethod(String methodName, Class<?>[] parameterTypes, Class<?> clazz);

    default void addMethodsAllPublic(Class<?> clazz)
    {

        addReflectionMethodsAllPublic(clazz.getName());
    }

    void addReflectionMethodsAllPublic(String clazz);

    void addRegisterServiceCalls(RegisterServiceCall registerServiceCall);

    void addResourceConfig(ResourceConfiguration resourceConfig);

    Stream<Path> getFiles(FileType... fileType);

    List<RegisterServiceCall> getRegisterServiceCalls();

    ResourceConfiguration getResourceConfig();

    DynamicProxyConfiguration getDynamicProxyConfig();

    void addDynamicProxyConfigs(String... items);

    ReflectionConfiguration getReflectConfig();

}
