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
package org.apache.felix.atomos.maven.index;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;

import org.apache.felix.atomos.maven.LauncherBuilderUtil;
import org.apache.felix.atomos.maven.configs.MavenClassPathConfig;
import org.apache.felix.atomos.maven.configs.MavenClasspathMavenConfig;
import org.apache.felix.atomos.maven.configs.MavenIndexConfig;
import org.apache.felix.atomos.utils.api.Launcher;
import org.apache.felix.atomos.utils.api.LauncherBuilder;
import org.apache.felix.atomos.utils.core.plugins.collector.PathCollectorPlugin;
import org.apache.felix.atomos.utils.core.plugins.collector.PathCollectorPluginConfig;
import org.apache.felix.atomos.utils.core.plugins.index.IndexOutputType;
import org.apache.felix.atomos.utils.core.plugins.index.IndexPlugin;
import org.apache.felix.atomos.utils.core.plugins.index.IndexPluginConfig;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name = "atomos-index", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class AtomosIndexMojo extends AbstractMojo
{
    public static boolean isJarFile(Artifact a)
    {
        try (JarFile j = new JarFile(a.getFile());)
        {
            return true;
        }
        catch (IOException e)
        {
        }
        return false;
    }

    @Parameter(defaultValue = "${project}", required = true, readonly = false)
    private MavenProject project;

    @Parameter(required = false, readonly = false)
    private MavenIndexConfig index;

    @Parameter(required = false, readonly = false)
    private MavenClassPathConfig classpath;

    //    @Parameter(required = false, readonly = false)
    //    private MavenShadeConfig shade;

    @Override
    public void execute() throws MojoExecutionException
    {
        if (index == null)
        {
            index = new MavenIndexConfig();
        }
        if (classpath == null)
        {
            MavenClassPathConfig classPathConfig = new MavenClassPathConfig();
            MavenClasspathMavenConfig mavenIndexMojoConfig = new MavenClasspathMavenConfig();
            classPathConfig.maven = List.of(mavenIndexMojoConfig);
            classpath = classPathConfig;
        }
        if (index.indexOutputType == null)
        {
            index.indexOutputType = IndexOutputType.DIRECTORY;//TODO: ??? IndexOutputType.DIRECTORY;
        }
        try
        {
            File outputDirectory = project.getBasedir();
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

            //Index-Plugin

            IndexPluginConfig ic = LauncherBuilderUtil.processIndex(index, project);

            builder.addPlugin(IndexPlugin.class, ic);
            //Shade
            //TODO: use own shader
            //            ShadeConfig sc = LauncherBuilderUtil.processShade(builder, shade, project);
            //            builder.addPlugin(ShaderPlugin.class, sc);

            builder.build().execute();

        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error", e);
        }

    }

}
