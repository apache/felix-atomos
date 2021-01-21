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
package org.apache.felix.atomos.maven.configs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

public class MavenNativeImageConfig
{
    final private static String ATOMOS_PATH = "ATOMOS";
    @Parameter(required = false, readonly = false)
    public List<String> additionalInitializeAtBuildTime = new ArrayList<>();
    @Parameter(required = false, readonly = false)
    public List<File> dynamicProxyConfigurationFiles = new ArrayList<>();
    @Parameter(required = false, readonly = false)
    public List<File> reflectionConfigurationFiles = new ArrayList<>();
    @Parameter(required = false, readonly = false)
    public List<File> resourceConfigurationFiles = new ArrayList<>();
    @Parameter(required = false, readonly = false)
    public Boolean noFallBack;

    @Parameter(defaultValue = "false") //TODO: CHECK GRAAL EE ONLY
    public boolean debug;

    @Parameter(defaultValue = "${project.artifactId}", required = false, readonly = false)
    public String applicationName;

    @Parameter(required = false, readonly = false)
    public String mainClass;

    @Parameter(defaultValue = "${project.build.directory}/" + ATOMOS_PATH)
    public File outputDirectory;
    @Parameter(defaultValue = "graal.native.image.build.args")
    public String nativeImageArgsPropertyName;

    @Parameter
    public File nativeImageExecutable;

    @Parameter
    public List<String> vmFlags;

    @Parameter
    public Map<String, String> vmSystemProperties;

}
