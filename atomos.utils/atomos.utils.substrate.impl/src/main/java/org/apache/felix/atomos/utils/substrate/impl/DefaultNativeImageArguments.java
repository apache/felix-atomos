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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface DefaultNativeImageArguments extends BaseNativeImageArguments
{
    String NI_DEFAULT_NAME = "Application";

    @Override
    default List<String> additionalArguments()
    {
        return List.of();
    }

    @Override
    default boolean allowIncompleteClasspath()
    {
        return true;
    }

    @Override
    default List<Path> classPathFiles()
    {
        return List.of();
    }

    @Override
    default boolean debugAttach()
    {
        return false;
    }

    @Override
    default List<Path> dynamicProxyConfigurationFiles()
    {
        return List.of();
    }

    @Override
    default List<String> initializeAtBuildTime()
    {
        return List.of();
    }

    @Override
    default String name()
    {
        return NI_DEFAULT_NAME;
    }

    @Override
    default boolean noFallback()
    {

        return false;
    }

    @Override
    default List<Path> reflectionConfigurationFiles()
    {
        return List.of();
    }

    @Override
    default boolean reportExceptionStackTraces()
    {

        return false;
    }

    @Override
    default boolean reportUnsupportedElementsAtRuntime()
    {

        return false;
    }

    @Override
    default List<Path> resourceConfigurationFiles()
    {
        return List.of();
    }

    @Override
    default boolean traceClassInitialization()
    {

        return false;
    }

    @Override
    default List<String> vmFlags()
    {
        return List.of();
    }

    @Override
    default Map<String, String> vmSystemProperties()
    {
        // TODO Auto-generated method stub
        return Map.of();
    }

}
