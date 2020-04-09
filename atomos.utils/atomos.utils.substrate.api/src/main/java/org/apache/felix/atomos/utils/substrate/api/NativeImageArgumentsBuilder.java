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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;
public interface NativeImageArgumentsBuilder
{
    NativeImageArgumentsBuilder allowIncompleteClasspath(
        boolean allowIncompleteClasspath);
    NativeImageArguments build();
    NativeImageArgumentsBuilder classPathFile(Optional<Path> classPathFile);
    NativeImageArgumentsBuilder classPathFiles(Optional<List<Path>> classPathFiles);
    NativeImageArgumentsBuilder debugAttach(boolean debugAttach);
    NativeImageArgumentsBuilder dynamicProxyConfiguration(
        Optional<DynamicProxyConfiguration> dynamicProxyConfiguration);
    NativeImageArgumentsBuilder dynamicProxyConfigurationFile(
        Optional<Path> dynamicProxyConfigurationFile);
    NativeImageArgumentsBuilder dynamicProxyConfigurationFiles(
        Optional<List<Path>> dynamicProxyConfigurationFiles);
    NativeImageArgumentsBuilder dynamicProxyConfigurations(
        Optional<List<DynamicProxyConfiguration>> dynamicProxyConfigurations);
    NativeImageArgumentsBuilder imageName(String imageName);
    NativeImageArgumentsBuilder initializeAtBuildTimePackage(
        Optional<String> initializeAtBuildTimePackage);
    NativeImageArgumentsBuilder initializeAtBuildTimePackages(
        Optional<List<String>> initializeAtBuildTimePackages);
    NativeImageArgumentsBuilder mainClass(String mainClass);
    NativeImageArgumentsBuilder noFallback(boolean noFallback);
    NativeImageArgumentsBuilder reflectionConfiguration(
        Optional<ReflectionConfiguration> reflectionConfiguration);
    NativeImageArgumentsBuilder reflectionConfigurationFile(
        Optional<Path> reflectionConfigurationFile);
    NativeImageArgumentsBuilder reflectionConfigurationFiles(
        Optional<List<Path>> reflectionConfigurationFiles);
    NativeImageArgumentsBuilder reflectionConfigurations(
        Optional<List<ReflectionConfiguration>> reflectionConfigurations);
    NativeImageArgumentsBuilder reportExceptionStackTraces(
        boolean reportExceptionStackTraces);
    NativeImageArgumentsBuilder reportUnsupportedElementsAtRuntime(
        boolean reportUnsupportedElementsAtRuntime);
    NativeImageArgumentsBuilder resourceConfiguration(
        Optional<ResourceConfiguration> resourceConfiguration);
    NativeImageArgumentsBuilder resourceConfigurationFile(
        Optional<Path> resourceConfigurationFile);
    NativeImageArgumentsBuilder resourceConfigurationFiles(
        Optional<List<Path>> resourceConfigurationFiles);
    NativeImageArgumentsBuilder resourceConfigurations(
        Optional<List<ResourceConfiguration>> resourceConfigurations);
    NativeImageArgumentsBuilder traceClassInitialization(
        boolean traceClassInitialization);
    NativeImageArgumentsBuilder vmFlag(Optional<String> vmFlag);
    NativeImageArgumentsBuilder vmFlags(Optional<List<String>> vmFlags);
    NativeImageArgumentsBuilder vmSystemProperties(
        Optional<Map<String, String>> vmSystemProperties);
    NativeImageArgumentsBuilder vmSystemProperty(String key, String value);
}