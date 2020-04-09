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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NativeImageArgumentsImpl implements DefaultNativeImageArguments
{

    private static List<String> addArgIfExitsPath(final List<String> arguments,
        final String parameterName, final List<Path> values)
    {
        if (values != null && !values.isEmpty())
        {
            arguments.add(combineArgPath(parameterName, values.stream()));
        }
        return arguments;
    }

    private static String combineArg(final String parameterName,
        final Stream<String> values)
    {
        return combineArg(parameterName,
            values.filter(Objects::nonNull).sorted().collect(Collectors.joining(",")));
    }

    private static String combineArg(final String parameterName, final String value)
    {
        return parameterName + "=" + value;
    }

    private static String combineArgPath(final String parameterName,
        final Stream<Path> values)
    {
        return combineArg(parameterName,
            values.filter(Objects::nonNull).map(Path::toAbsolutePath).map(
                Path::toString));
    }

    boolean allowIncompleteClasspath = false;
    final List<Path> classPathFiles = new ArrayList<>();
    boolean debugAttach = false;
    final List<Path> dynamicProxyConfigurationFiles = new ArrayList<>();
    String imageName;
    final List<String> initializeAtBuildTime = new ArrayList<>();
    String mainClass;
    boolean noFallback = true;
    final List<Path> reflectionConfigurationFiles = new ArrayList<>();
    boolean reportExceptionStackTraces = false;
    boolean reportUnsupportedElementsAtRuntime = false;
    final List<Path> resourceConfigurationFiles = new ArrayList<>();
    boolean traceClassInitialization = false;
    boolean verbose = true;
    final List<String> vmFlags = new ArrayList<>();
    final Map<String, String> vmSystemProperties = new HashMap<>();

    NativeImageArgumentsImpl()
    {
    }

    private List<String> addArgIfExits(final List<String> arguments,
        final String parameterName, final List<String> values)
    {
        if (values != null && !values.isEmpty())
        {
            arguments.add(combineArg(parameterName, values.stream()));
        }
        return arguments;
    }

    private List<String> addArgIfTrue(final List<String> arguments,
        final String parameterName, final boolean value)
    {
        if (value)
        {
            arguments.add(parameterName);
        }
        return arguments;
    }

    @Override
    public boolean allowIncompleteClasspath()
    {

        return allowIncompleteClasspath;
    }

    @Override
    public List<String> arguments()
    {
        final List<String> arguments = new ArrayList<>();
        //-cp
        arguments.add(NI_PARAM_CP);
        String cp = classPathFiles().stream().filter(Objects::nonNull).map(
            Path::toAbsolutePath).map(Path::toString).collect(Collectors.joining(":"));
        arguments.add(cp);

        //--verbose
        addArgIfTrue(arguments, NI_PARAM_VERBOSE, verbose);
        //initialize-at-build-time
        addArgIfExits(arguments, NI_PARAM_INITIALIZE_AT_BUILD_TIME,
            initializeAtBuildTime());
        //H:ReflectionConfigurationFiles
        addArgIfExitsPath(arguments, NI_PARAM_H_REFLECTION_CONFIGURATION_FILES,
            reflectionConfigurationFiles());
        //H:ResourceConfigurationFiles
        addArgIfExitsPath(arguments, NI_PARAM_H_RESOURCE_CONFIGURATION_FILES,
            resourceConfigurationFiles());
        //H:DynamicProxyConfigurationFiles
        addArgIfExitsPath(arguments, NI_PARAM_H_DYNAMIC_PROXY_CONFIGURATION_FILES,
            dynamicProxyConfigurationFiles());
        //--allow-incomplete-classpath
        addArgIfTrue(arguments, NI_PARAM_ALLOW_INCOMPLETE_CLASSPATH,
            allowIncompleteClasspath());
        //-H:+ReportUnsupportedElementsAtRuntime
        addArgIfTrue(arguments, NI_PARAM_H_REPORT_UNSUPPORTED_ELEMENTS_AT_RUNTIME,
            reportUnsupportedElementsAtRuntime());
        //-H:+ReportExceptionStackTraces
        addArgIfTrue(arguments, NI_PARAM_H_REPORT_EXCEPTION_STACK_TRACES,
            reportExceptionStackTraces());
        //
        addArgIfTrue(arguments, NI_PARAM_H_TRACE_CLASS_INITIALIZATION,
            allowIncompleteClasspath());
        //-H:+TraceClassInitialization
        addArgIfTrue(arguments, NI_PARAM_H_PRINT_CLASS_INITIALIZATION,
            traceClassInitialization());
        //--no-fallback
        addArgIfTrue(arguments, NI_PARAM_NO_FALLBACK, noFallback());
        //--debug-attach
        addArgIfTrue(arguments, NI_PARAM_DEBUG_ATTACH, debugAttach());
        //-H:Class
        arguments.add(combineArg(NI_PARAM_H_CLASS, mainClass()));
        //-H:Name"
        arguments.add(combineArg(NI_PARAM_H_NAME, name()));
        //-D<name>=<value> sets a system property for the JVM running the image generator
        vmSystemProperties().forEach((k, v) -> {
            arguments.add(combineArg("-D" + k, v));
        });
        vmFlags().forEach(flag -> {
            arguments.add("-J" + flag);
        });
        final List<String> additionalArguments = additionalArguments();
        if (additionalArguments != null && !additionalArguments.isEmpty())
        {
            arguments.addAll(additionalArguments());
        }
        return arguments;
    }

    @Override
    public List<Path> classPathFiles()
    {
        return classPathFiles;
    }

    @Override
    public boolean debugAttach()
    {

        return debugAttach;
    }

    @Override
    public List<Path> dynamicProxyConfigurationFiles()
    {
        return dynamicProxyConfigurationFiles;
    }

    @Override
    public List<String> initializeAtBuildTime()
    {
        return initializeAtBuildTime;
    }

    @Override
    public String mainClass()
    {
        return mainClass;
    }

    @Override
    public String name()
    {
        return imageName;
    }

    @Override
    public boolean noFallback()
    {
        return noFallback;
    }

    @Override
    public List<Path> reflectionConfigurationFiles()
    {
        return reflectionConfigurationFiles;
    }

    @Override
    public boolean reportExceptionStackTraces()
    {
        return reportExceptionStackTraces;
    }

    @Override
    public boolean reportUnsupportedElementsAtRuntime()
    {
        return reportUnsupportedElementsAtRuntime;
    }

    @Override
    public List<Path> resourceConfigurationFiles()
    {
        return resourceConfigurationFiles;
    }

    @Override
    public boolean traceClassInitialization()
    {
        return traceClassInitialization;
    }

    @Override
    public List<String> vmFlags()
    {
        return vmFlags;
    }

    @Override
    public Map<String, String> vmSystemProperties()
    {
        return vmSystemProperties;
    }

    @Override
    public boolean verbose()
    {

        return verbose;
    }
}
