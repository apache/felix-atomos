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
package org.apache.felix.atomos.maven.nativeimage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.apache.felix.atomos.maven.LauncherBuilderUtil;
import org.apache.felix.atomos.maven.configs.MavenClassPathConfig;
import org.apache.felix.atomos.maven.configs.MavenClasspathMavenConfig;
import org.apache.felix.atomos.maven.configs.MavenIndexConfig;
import org.apache.felix.atomos.maven.configs.MavenNativeImageConfig;
import org.apache.felix.atomos.utils.api.Config;
import org.apache.felix.atomos.utils.api.Launcher;
import org.apache.felix.atomos.utils.api.LauncherBuilder;
import org.apache.felix.atomos.utils.core.plugins.ComponentDescriptionPlugin;
import org.apache.felix.atomos.utils.core.plugins.GogoPlugin;
import org.apache.felix.atomos.utils.core.plugins.OsgiDTOPlugin;
import org.apache.felix.atomos.utils.core.plugins.ResourcePlugin;
import org.apache.felix.atomos.utils.core.plugins.activator.InvocatingBundleActivatorPlugin;
import org.apache.felix.atomos.utils.core.plugins.activator.ReflectionBundleActivatorPlugin;
import org.apache.felix.atomos.utils.core.plugins.collector.PathCollectorPlugin;
import org.apache.felix.atomos.utils.core.plugins.collector.PathCollectorPluginConfig;
import org.apache.felix.atomos.utils.core.plugins.finaliser.ni.NativeImageBuilderConfig;
import org.apache.felix.atomos.utils.core.plugins.finaliser.ni.NativeImagePlugin;
import org.apache.felix.atomos.utils.core.plugins.index.IndexOutputType;
import org.apache.felix.atomos.utils.core.plugins.index.IndexPlugin;
import org.apache.felix.atomos.utils.core.plugins.index.IndexPluginConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name = "atomos-native-image", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class NativeImageMojo extends AbstractMojo
{

    @Parameter(required = false, readonly = false)
    public MavenNativeImageConfig nativeImage;
    @Parameter(defaultValue = "${project}", required = true, readonly = false)
    private MavenProject project;
    @Parameter(required = false, readonly = false)
    MavenClassPathConfig classpath;

    @Override
    public void execute() throws MojoExecutionException
    {

        if (nativeImage.applicationName == null)
        {
            nativeImage.applicationName = project.getArtifactId();
        }
        try
        {
            if (classpath == null)
            {
                MavenClassPathConfig classPathConfig = new MavenClassPathConfig();
                MavenClasspathMavenConfig mavenIndexMojoConfig = new MavenClasspathMavenConfig();
                classPathConfig.maven = List.of(mavenIndexMojoConfig);
                classpath = classPathConfig;
            }
            File outputDirectory = Paths.get(project.getBuild().getDirectory()).toFile();
            Files.createDirectories(outputDirectory.toPath());

            LauncherBuilder builder = Launcher.builder();

            //Collect files using paths and filters
            Optional.ofNullable(classpath.paths)//
                .orElse(List.of())//
                .forEach(cc -> {
                    PathCollectorPluginConfig pc = LauncherBuilderUtil.processClasspathFile(
                        cc);
                    builder.addPlugin(PathCollectorPlugin.class, pc);
                });

            //Collect files from maven project
            Optional.ofNullable(classpath.maven)//
                .orElse(List.of())//
                .forEach(cc -> {
                    PathCollectorPluginConfig pc = LauncherBuilderUtil.processClasspathMaven(
                        project, cc);
                    builder.addPlugin(PathCollectorPlugin.class, pc);
                });

            MavenIndexConfig mic = new MavenIndexConfig();
            mic.indexOutputType = IndexOutputType.JAR;
            //Index-Plugin
            IndexPluginConfig ic = LauncherBuilderUtil.processIndex(mic, project);
            builder.addPlugin(IndexPlugin.class, ic);

            //
            Config cfg = new Config()
            {
            };
            builder//
                .addPlugin(ReflectionBundleActivatorPlugin.class, cfg)//
                .addPlugin(ComponentDescriptionPlugin.class, cfg)//
                .addPlugin(GogoPlugin.class, cfg)//
                .addPlugin(InvocatingBundleActivatorPlugin.class, cfg)//
                .addPlugin(OsgiDTOPlugin.class, cfg)//
                .addPlugin(ResourcePlugin.class, cfg);//

            //Naitve image

            if (nativeImage.outputDirectory == null)
            {
                nativeImage.outputDirectory = outputDirectory;
            }
            NativeImageBuilderConfig nic = LauncherBuilderUtil.processNativeImageConfig(
                nativeImage, project);
            builder.addPlugin(NativeImagePlugin.class, nic);

            builder.build().execute();

        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error", e);
        }
    }
}
