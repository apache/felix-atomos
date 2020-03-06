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
package org.apache.felix.atomos.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.felix.atomos.maven.ResourceConfigUtil.ResourceConfigResult;
import org.apache.felix.atomos.maven.reflect.ReflectConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "atomos-native-image", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class NativeImageMojo extends AbstractMojo
{

    private class Config
    {
        public List<String> additionalInitializeAtBuildTime = new ArrayList<>();

        public List<Path> dynamicProxyConfigurationFiles = new ArrayList<>();
        public List<Path> reflectConfigFiles = new ArrayList<>();
        public List<Path> resourceConfigs = new ArrayList<>();
    }

    final private static String ATOMOS_PATH = "ATOMOS";

    public static boolean isJarFile(Path path)
    {
        try (JarFile j = new JarFile(path.toFile());)
        {

            return true;
        }
        catch (IOException e)
        {

        }

        return false;
    }

    @Parameter
    private List<String> additionalInitializeAtBuildTime;

    @Parameter(defaultValue = "${project.build.directory}/" + "classpath_lib")
    private File classpath_lib;

    @Parameter(defaultValue = "false") //TODO: CHECK GRAAL EE ONLY
    private boolean debug;

    @Parameter
    private List<File> dynamicProxyConfigurationFiles;

    @Parameter
    private List<File> graalResourceConfigFiles;

    @Parameter
    private String imageName;

    @Parameter
    private String mainClass;

    @Parameter(defaultValue = "graal.native.image.build.args")
    private String nativeImageArgsPropertyName;

    @Parameter
    private String nativeImageExecutable;

    @Parameter(defaultValue = "${project.build.directory}/" + ATOMOS_PATH)
    private File outputDirectory;

    @Parameter(defaultValue = "${project}", required = true, readonly = false)
    private MavenProject project;

    @Parameter
    private List<File> reflectConfigFiles;

    @Override
    public void execute() throws MojoExecutionException
    {
        getLog().info("outputDirectory" + outputDirectory);
        try
        {
            Files.createDirectories(outputDirectory.toPath());

            Config config = new Config();

            config.additionalInitializeAtBuildTime = additionalInitializeAtBuildTime;
            if (imageName == null || imageName.isEmpty())
            {
                imageName = project.getArtifactId();
            }

            if (graalResourceConfigFiles != null && !graalResourceConfigFiles.isEmpty())
            {
                config.resourceConfigs = graalResourceConfigFiles.stream().map(
                    File::toPath).collect(Collectors.toList());
            }

            if (reflectConfigFiles != null && !reflectConfigFiles.isEmpty())
            {
                config.reflectConfigFiles = reflectConfigFiles.stream().map(
                    File::toPath).collect(Collectors.toList());
            }

            if (dynamicProxyConfigurationFiles != null
                && !dynamicProxyConfigurationFiles.isEmpty())
            {
                config.dynamicProxyConfigurationFiles = dynamicProxyConfigurationFiles.stream().map(
                    File::toPath).collect(Collectors.toList());
            }

            List<Path> paths = Files.list(classpath_lib.toPath()).filter(
                NativeImageMojo::isJarFile).collect(Collectors.toList());

            Path p = SubstrateUtil.substrate(paths, outputDirectory.toPath());

            List<ReflectConfig> reflectConfigs = ReflectConfigUtil.reflectConfig(paths);

            String content = ReflectConfigUtil.json(reflectConfigs);

            if (!content.isEmpty())
            {
                Path reflectConfig = outputDirectory.toPath().resolve(
                    "graal_reflect_config.json");
                Files.write(reflectConfig, content.getBytes());

                config.reflectConfigFiles.add(reflectConfig);
            }

            ResourceConfigResult resourceConfigResult = ResourceConfigUtil.resourceConfig(
                paths);

            List<String> argsPath = NativeImageBuilder.createExecutionArgs(
                config.additionalInitializeAtBuildTime, config.reflectConfigFiles,
                config.resourceConfigs, config.dynamicProxyConfigurationFiles,
                resourceConfigResult, false, mainClass, imageName);

            paths.add(p);
            Path nip = nativeImageExecutable == null ? null
                : Paths.get(nativeImageExecutable);
            NativeImageBuilder.execute(nip, outputDirectory.toPath(), paths, argsPath);
        }
        catch (

            Exception e)
        {
            throw new MojoExecutionException("Error", e);
        }

    }
}
