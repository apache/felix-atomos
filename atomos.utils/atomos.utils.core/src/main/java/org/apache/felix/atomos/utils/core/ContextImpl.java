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

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.api.RegisterServiceCall;
import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultDynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionClassConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionConstructorConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionFieldConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionMethodConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultResourceConfiguration;

public class ContextImpl implements Context
{

    private final Map<Path, FileType> paths = new HashMap<>();

    private final Collection<DefaultReflectionClassConfiguration> reflectConfigs = new ArrayList<>();

    private final List<RegisterServiceCall> registerServiceCalls = new ArrayList<>();

    private final DefaultResourceConfiguration resourceConfig = new DefaultResourceConfiguration();

    private final DefaultDynamicProxyConfiguration dynamicProxyConfigs = new DefaultDynamicProxyConfiguration();

    @Override
    public void addDynamicProxyConfigs(String... items)
    {
        dynamicProxyConfigs.addItem(items);
    }

    @Override
    public void addFile(Path path, FileType type)
    {
        //maybe copy
        paths.put(path, type);
    }

    @Override
    //parameterTypes==null means not Set
    //parameterTypes=={} means no method parameter
    public void addReflecionMethod(String mName, Class<?>[] parameterTypes,
        Class<?> clazz)
    {
        for (Method m : clazz.getDeclaredMethods())
        {
            if (mName.equals(m.getName()))
            {
                if (parameterTypes == null)
                {
                    DefaultReflectionClassConfiguration config = computeIfAbsent(
                        clazz.getName());
                    config.add(new DefaultReflectionMethodConfiguration(mName, null));
                    break;
                }
                else if (Arrays.equals(m.getParameterTypes(), parameterTypes))
                {
                    DefaultReflectionClassConfiguration config = computeIfAbsent(
                        clazz.getName());
                    String[] sParameterTypes = Stream.of(parameterTypes).sequential().map(
                        Class::getName).toArray(String[]::new);
                    config.add(
                        new DefaultReflectionMethodConfiguration(mName, sParameterTypes));
                    break;
                }
            }
        }
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null)
        {
            addReflecionMethod(mName, parameterTypes, superClass);
        }
    }

    @Override
    public void addReflectionClass(String className)
    {
        computeIfAbsent(className);
    }

    @Override
    public void addReflectionConstructor(String bundleActivatorClassName,
        String[] parameterTypeNames)
    {
        computeIfAbsent(bundleActivatorClassName).add(
            new DefaultReflectionConstructorConfiguration(parameterTypeNames));
    }

    @Override
    public void addReflectionConstructorAllPublic(String clazzName)
    {
        computeIfAbsent(clazzName).setAllPublicConstructors(true);
    }

    @Override
    public void addReflectionField(String fName, Class<?> clazz)
    {

        boolean exists = Stream.of(clazz.getDeclaredFields()).anyMatch(
            f -> f.getName().equals(fName));
        if (exists)
        {
            DefaultReflectionClassConfiguration config = computeIfAbsent(clazz.getName());
            config.add(new DefaultReflectionFieldConfiguration(fName));
        }

        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null)
        {
            addReflectionField(fName, superClass);
        }

    }

    @Override
    public void addReflectionFieldsAllPublic(String className)
    {
        computeIfAbsent(className).setAllPublicFields(true);
    }

    @Override
    public void addReflectionMethodsAllPublic(String className)
    {
        computeIfAbsent(className).setAllPublicMethods(true);
    }

    @Override
    public void addRegisterServiceCalls(RegisterServiceCall registerServiceCall)
    {

        registerServiceCalls.add(registerServiceCall);

    }

    @Override
    public void addResourceConfig(ResourceConfiguration resourceConfig)
    {
        this.resourceConfig.addResourcePackage(resourceConfig.getResourcePackages());
        this.resourceConfig.addResourcePattern(resourceConfig.getResourcePatterns());
        this.resourceConfig.addResourceBundle(resourceConfig.getResourceBundles());

    }

    private DefaultReflectionClassConfiguration computeIfAbsent(final String className)
    {
        Optional<DefaultReflectionClassConfiguration> oConfig = reflectConfigs.stream().filter(
            c -> c.getClassName().equals(className)).findFirst();

        DefaultReflectionClassConfiguration rc = null;
        if (oConfig.isPresent())
        {
            rc = oConfig.get();
        }
        else
        {
            rc = new DefaultReflectionClassConfiguration(className);
            reflectConfigs.add(rc);
        }
        return rc;
    }

    @Override
    public DynamicProxyConfiguration getDynamicProxyConfig()
    {
        return dynamicProxyConfigs;
    }

    @Override
    public Stream<Path> getFiles(FileType... fileType)
    {
        return paths.entrySet().parallelStream().filter(
            e -> List.of(fileType).stream().filter(Objects::nonNull).anyMatch(
                t -> t.equals(e.getValue()))).map(Entry::getKey);
    }

    Map<Path, FileType> getPaths()
    {
        return Map.copyOf(paths);
    }

    @Override
    public ReflectionConfiguration getReflectConfig()
    {
        return new DefaultReflectionConfiguration(List.copyOf(reflectConfigs));
    }

    @Override
    public List<RegisterServiceCall> getRegisterServiceCalls()
    {
        return registerServiceCalls;
    }

    @Override
    public ResourceConfiguration getResourceConfig()
    {
        return resourceConfig;
    }

}
