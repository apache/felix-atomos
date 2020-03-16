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
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

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

    @Override
    public void execute() throws MojoExecutionException
    {
        try
        {
            File outputDirectory = new File(project.getBuild().getOutputDirectory());
            Files.createDirectories(outputDirectory.toPath());

            List<Path> paths = project.getArtifacts().stream().filter(
                AtomosIndexMojo::isJarFile).map(a -> a.getFile().toPath()).collect(
                    Collectors.toList());

            SubstrateUtil.indexContent(paths, outputDirectory.toPath());
        }
        catch (

            Exception e)
        {
            throw new MojoExecutionException("Error", e);
        }

    }
}
