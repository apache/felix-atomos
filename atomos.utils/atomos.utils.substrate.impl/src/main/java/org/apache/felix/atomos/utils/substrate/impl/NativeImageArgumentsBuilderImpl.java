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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.felix.atomos.utils.substrate.api.NativeImageArgumentsBuilder;
import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.json.DynamicProxyJsonUtil;
import org.apache.felix.atomos.utils.substrate.impl.json.ReflectJsonUtil;
import org.apache.felix.atomos.utils.substrate.impl.json.ResourceJsonUtil;
@aQute.bnd.annotation.spi.ServiceProvider(NativeImageArgumentsBuilder.class)
public class NativeImageArgumentsBuilderImpl implements NativeImageArgumentsBuilder
{
    private final NativeImageArgumentsImpl a;
    public NativeImageArgumentsBuilderImpl()
    {
        a = new NativeImageArgumentsImpl();
    }
    @Override
    public NativeImageArgumentsBuilder allowIncompleteClasspath(
        final boolean allowIncompleteClasspath)
    {
        a.allowIncompleteClasspath = allowIncompleteClasspath;
        return this;
    }
    @Override
    public BaseNativeImageArguments build()
    {
        //todo verify;
        return a;
    }
    @Override
    public NativeImageArgumentsBuilder classPathFile(final Optional<Path> classPathFile)
    {
        classPathFile.ifPresent((a.classPathFiles::add));
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder classPathFiles(
        final Optional<List<Path>> classPathFiles)
    {
        classPathFiles.ifPresent((a.classPathFiles::addAll));
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder debugAttach(final boolean debugAttach)
    {
        a.debugAttach = debugAttach;
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder dynamicProxyConfiguration(
        final Optional<DynamicProxyConfiguration> dynamicProxyConfiguration)
    {
        dynamicProxyConfiguration.ifPresent(this::processDynamicProxyConfiguration);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder dynamicProxyConfigurationFile(
        final Optional<Path> dynamicProxyConfigurationFile)
    {
        dynamicProxyConfigurationFile.ifPresent(a.dynamicProxyConfigurationFiles::add);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder dynamicProxyConfigurationFiles(
        final Optional<List<Path>> dynamicProxyConfigurationFiles)
    {
        dynamicProxyConfigurationFiles.ifPresent(
            a.dynamicProxyConfigurationFiles::addAll);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder dynamicProxyConfigurations(
        final Optional<List<DynamicProxyConfiguration>> dynamicProxyConfigurations)
    {
        dynamicProxyConfigurations.ifPresent(
            list -> list.forEach(this::processDynamicProxyConfiguration));
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder imageName(final String imageName)
    {
        a.imageName = imageName;
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder initializeAtBuildTimePackage(
        final Optional<String> initializeAtBuildTimePackage)
    {
        initializeAtBuildTimePackage.ifPresent(a.initializeAtBuildTime::add);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder initializeAtBuildTimePackages(
        final Optional<List<String>> initializeAtBuildTimePackages)
    {
        initializeAtBuildTimePackages.ifPresent(a.initializeAtBuildTime::addAll);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder mainClass(final String mainClass)
    {
        a.mainClass = mainClass;
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder noFallback(final boolean noFallback)
    {
        a.noFallback = noFallback;
        return this;
    }
    private void processDynamicProxyConfiguration(
        final DynamicProxyConfiguration dynamicProxyConfiguration)
    {
        final Path p = write("dynamicProxyConfiguration",
            DynamicProxyJsonUtil.json(dynamicProxyConfiguration));
        dynamicProxyConfigurationFile(Optional.of(p));
    }
    private void processReflectConfiguration(
        final ReflectionConfiguration reflectConfiguration)
    {
        final Path p = write("reflectConfiguration",
            ReflectJsonUtil.json(reflectConfiguration));
        reflectionConfigurationFile(Optional.of(p));
    }
    private void processResourceConfiguration(
        final ResourceConfiguration resourceConfiguration)
    {
        final Path p = write("resourceConfiguration",
            ResourceJsonUtil.json(resourceConfiguration));
        resourceConfigurationFile(Optional.of(p));
    }
    @Override
    public NativeImageArgumentsBuilder reflectionConfiguration(
        final Optional<ReflectionConfiguration> reflectionConfiguration)
    {
        reflectionConfiguration.ifPresent(this::processReflectConfiguration);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder reflectionConfigurationFile(
        final Optional<Path> reflectionConfigurationFile)
    {
        reflectionConfigurationFile.ifPresent(a.reflectionConfigurationFiles::add);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder reflectionConfigurationFiles(
        final Optional<List<Path>> reflectionConfigurationFiles)
    {
        reflectionConfigurationFiles.ifPresent(a.reflectionConfigurationFiles::addAll);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder reflectionConfigurations(
        final Optional<List<ReflectionConfiguration>> reflectConfiguration)
    {
        reflectConfiguration.ifPresent(
            list -> list.forEach(this::processReflectConfiguration));
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder reportExceptionStackTraces(
        final boolean reportExceptionStackTraces)
    {
        a.reportExceptionStackTraces = reportExceptionStackTraces;
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder reportUnsupportedElementsAtRuntime(
        final boolean reportUnsupportedElementsAtRuntime)
    {
        a.reportUnsupportedElementsAtRuntime = reportUnsupportedElementsAtRuntime;
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder resourceConfiguration(
        final Optional<ResourceConfiguration> resourceConfiguration)
    {
        resourceConfiguration.ifPresent(this::processResourceConfiguration);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder resourceConfigurationFile(
        final Optional<Path> resourceConfigurationFile)
    {
        resourceConfigurationFile.ifPresent(a.resourceConfigurationFiles::add);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder resourceConfigurationFiles(
        final Optional<List<Path>> resourceConfigurationFiles)
    {
        resourceConfigurationFiles.ifPresent(a.resourceConfigurationFiles::addAll);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder resourceConfigurations(
        final Optional<List<ResourceConfiguration>> resourceConfigurations)
    {
        resourceConfigurations.ifPresent(
            list -> list.forEach(this::processResourceConfiguration));
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder traceClassInitialization(
        final boolean traceClassInitialization)
    {
        a.traceClassInitialization = traceClassInitialization;
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder vmFlag(final Optional<String> vmFlag)
    {
        vmFlag.ifPresent(a.vmFlags::add);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder vmFlags(final Optional<List<String>> vmFlags)
    {
        vmFlags.ifPresent(a.vmFlags::addAll);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder vmSystemProperties(
        final Optional<Map<String, String>> vmSystemProperties)
    {
        vmSystemProperties.ifPresent(a.vmSystemProperties::putAll);
        return this;
    }
    @Override
    public NativeImageArgumentsBuilder vmSystemProperty(final String key,
        final String value)
    {
        a.vmSystemProperties.put(key, value);
        return this;
    }
    private Path write(final String prefix, final String json)
    {
        try
        {
            return Files.createTempFile(prefix, "json");
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}