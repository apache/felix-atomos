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

import org.apache.felix.atomos.utils.substrate.api.NativeImageArguments;

public interface BaseNativeImageArguments extends NativeImageArguments
{
    String NI_PARAM_ALLOW_INCOMPLETE_CLASSPATH = "--allow-incomplete-classpath";

    String NI_PARAM_CP = "-cp";

    String NI_PARAM_VERBOSE = "--verbose";

    String NI_PARAM_DEBUG_ATTACH = "--debug-attach";

    String NI_PARAM_H_CLASS = "-H:Class";

    String NI_PARAM_H_DYNAMIC_PROXY_CONFIGURATION_FILES = "-H:DynamicProxyConfigurationFiles";

    String NI_PARAM_H_NAME = "-H:Name";

    String NI_PARAM_H_PRINT_CLASS_INITIALIZATION = "-H:+PrintClassInitialization";

    String NI_PARAM_H_REFLECTION_CONFIGURATION_FILES = "-H:ReflectionConfigurationFiles";

    String NI_PARAM_H_REPORT_EXCEPTION_STACK_TRACES = "-H:+ReportExceptionStackTraces";

    String NI_PARAM_H_REPORT_UNSUPPORTED_ELEMENTS_AT_RUNTIME = "-H:+ReportUnsupportedElementsAtRuntime";

    String NI_PARAM_H_RESOURCE_CONFIGURATION_FILES = "-H:ResourceConfigurationFiles";

    String NI_PARAM_H_TRACE_CLASS_INITIALIZATION = "-H:+TraceClassInitialization";

    String NI_PARAM_INITIALIZE_AT_BUILD_TIME = "--initialize-at-build-time";
    String NI_PARAM_NO_FALLBACK = "--no-fallback";

    List<String> additionalArguments();

    boolean allowIncompleteClasspath();

    List<Path> classPathFiles();

    boolean debugAttach();

    List<Path> dynamicProxyConfigurationFiles();

    List<String> initializeAtBuildTime();

    String mainClass();

    @Override
    String name();

    boolean noFallback();

    List<Path> reflectionConfigurationFiles();

    boolean reportExceptionStackTraces();

    boolean reportUnsupportedElementsAtRuntime();

    List<Path> resourceConfigurationFiles();

    boolean traceClassInitialization();

    boolean verbose();

    List<String> vmFlags();

    Map<String, String> vmSystemProperties();

}
