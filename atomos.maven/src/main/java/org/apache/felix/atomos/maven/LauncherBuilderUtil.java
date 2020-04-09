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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.maven.configs.MavenClassPathFileConfig;
import org.apache.felix.atomos.maven.configs.MavenClasspathMavenConfig;
import org.apache.felix.atomos.maven.configs.MavenIndexConfig;
import org.apache.felix.atomos.maven.configs.MavenNativeImageConfig;
import org.apache.felix.atomos.maven.configs.MavenShadeConfig;
import org.apache.felix.atomos.maven.index.AtomosIndexMojo;
import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.core.plugins.collector.PathCollectorPluginConfig;
import org.apache.felix.atomos.utils.core.plugins.finaliser.ni.NativeImageBuilderConfig;
import org.apache.felix.atomos.utils.core.plugins.finaliser.shade.ShadeConfig;
import org.apache.felix.atomos.utils.core.plugins.index.IndexOutputType;
import org.apache.felix.atomos.utils.core.plugins.index.IndexPluginConfig;
import org.apache.maven.project.MavenProject;

public class LauncherBuilderUtil
{
    public static PathCollectorPluginConfig processClasspathFile(

        MavenClassPathFileConfig filesIndexMojoConfig)
    {
        PathCollectorPluginConfig dc = new PathCollectorPluginConfig()
        {
            @Override
            public FileType fileType()
            {
                return filesIndexMojoConfig.fileType;
            }

            @Override
            public List<String> filters()
            {
                return filesIndexMojoConfig.filters;
            }

            @Override
            public List<Path> paths()
            {
                return Optional.ofNullable(filesIndexMojoConfig.files).orElse(
                    List.of()).stream().filter(Objects::nonNull).map(
                        File::toPath).collect(Collectors.toList());
            }
        };
        return dc;
    }

    public static PathCollectorPluginConfig processClasspathMaven(

        MavenProject project, MavenClasspathMavenConfig mavenIndexMojoConfig)
    {
        List<MavenProject> projects = new ArrayList<>();

        projects.add(project);
        if (mavenIndexMojoConfig.includingParent)
        {
            while (project.hasParent())
            {
                projects.add(project.getParent());
            }
        }

        List<Path> paths = projects.stream()//
            .flatMap(p -> p.getArtifacts().stream())//
            .filter(AtomosIndexMojo::isJarFile)//
            .filter(a -> {
                String scope = a.getScope().isEmpty() ? "*" : a.getScope();
                return Optional.ofNullable(mavenIndexMojoConfig.scopePatterns)//
                    .orElse(List.of(".*"))//
                    .stream()//
                    .anyMatch(s -> scope.matches(s));
            })//
            .filter(a -> {
                return Optional.ofNullable(mavenIndexMojoConfig.groupIdPattern)//
                    .orElse(List.of(".*"))//
                    .stream()//
                    .anyMatch(s -> a.getGroupId().matches(s));
            })//
            .filter(a -> {
                return Optional.ofNullable(mavenIndexMojoConfig.artefictIdPattern)//
                    .orElse(List.of(".*"))//
                    .stream()//
                    .anyMatch(s -> a.getArtifactId().matches(s));
            })//
            .map(a -> a.getFile().toPath())//
            .collect(Collectors.toList());

        PathCollectorPluginConfig dc = new PathCollectorPluginConfig()
        {

            @Override
            public FileType fileType()
            {
                return FileType.ARTIFACT;
            }

            @Override
            public List<String> filters()
            {
                return null;
            }

            @Override
            public List<Path> paths()
            {
                return paths;
            }
        };
        return dc;
    }

    public static IndexPluginConfig processIndex(MavenIndexConfig indexConfig,
        MavenProject project) throws IOException
    {

        Path indexOutputDirectoryPath = Optional.ofNullable(
            indexConfig.indexOutputDirectory)//
            .orElse(new File(project.getBuild().getOutputDirectory()))//
            .toPath();

        Files.createDirectories(indexOutputDirectoryPath);

        IndexPluginConfig ic = new IndexPluginConfig()
        {
            @Override
            public Path indexOutputDirectory()
            {
                return indexOutputDirectoryPath;
            }

            @Override
            public IndexOutputType indexOutputType()
            {
                return indexConfig.indexOutputType;
            }
        };
        return ic;
    }

    public static NativeImageBuilderConfig processNativeImageConfig(
        MavenNativeImageConfig nativeImageConfig, MavenProject project)
    {

        NativeImageBuilderConfig nic = new NativeImageBuilderConfig()
        {

            @Override
            public List<Path> dynamicProxyConfigurationFiles()
            {
                return Optional.ofNullable(
                    nativeImageConfig.dynamicProxyConfigurationFiles)//
                    .map(List::stream).orElse(Stream.of())//
                    .map(File::toPath)//
                    .collect(Collectors.toList());
            }

            @Override
            public List<Path> reflectionConfigurationFiles()
            {
                return Optional.ofNullable(nativeImageConfig.reflectionConfigurationFiles)//
                    .map(List::stream).orElse(Stream.of())//
                    .map(File::toPath)//
                    .collect(Collectors.toList());
            }

            @Override
            public List<Path> resourceConfigurationFiles()
            {
                return Optional.ofNullable(nativeImageConfig.resourceConfigurationFiles)//
                    .map(List::stream).orElse(Stream.of())//
                    .map(File::toPath)//
                    .collect(Collectors.toList());
            }

            @Override
            public List<String> nativeImageAdditionalInitializeAtBuildTime()
            {
                return nativeImageConfig.additionalInitializeAtBuildTime;
            }

            @Override
            public String nativeImageApplicationName()
            {
                return nativeImageConfig.applicationName;
            }

            @Override
            public Path nativeImageExecutable()
            {
                return Optional.ofNullable(nativeImageConfig.nativeImageExecutable).map(
                    File::toPath).orElse(null);
            }

            @Override
            public String nativeImageMainClass()
            {
                if (nativeImageConfig.mainClass == null
                    || nativeImageConfig.mainClass.isEmpty())
                {
                    return "org.apache.felix.atomos.launch.AtomosLauncher";
                }
                return nativeImageConfig.mainClass;
            }

            @Override
            public Path nativeImageOutputDirectory()
            {
                return Optional.ofNullable(nativeImageConfig.outputDirectory).map(
                    File::toPath).orElse(null);
            }

            @Override
            public List<String> nativeImageVmFlags()
            {
                return nativeImageConfig.vmFlags;
            }

            @Override
            public Map<String, String> nativeImageVmSystemProperties()
            {
                return nativeImageConfig.vmSystemProperties;

            }

            @Override
            public Boolean noFallback()
            {
                return nativeImageConfig.noFallBack;
            }
        };
        return nic;
    }

    public static ShadeConfig processShade(MavenShadeConfig shade, MavenProject project)
    {
        ShadeConfig sc = new ShadeConfig()
        {
            @Override
            public Path shadeOutputDirectory()
            {
                return shade.shadeOutputDirectory != null
                    ? shade.shadeOutputDirectory.toPath()
                        : Paths.get(project.getBuild().getDirectory());
            }
        };
        return sc;

    }
}
