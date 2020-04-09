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
package org.apache.felix.atomos.utils.core;

import static org.apache.felix.atomos.utils.core.TestConstants.getAllDependencys;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.api.Launcher;
import org.apache.felix.atomos.utils.core.plugins.ComponentDescriptionPlugin;
import org.apache.felix.atomos.utils.core.plugins.GogoPlugin;
import org.apache.felix.atomos.utils.core.plugins.OsgiDTOPlugin;
import org.apache.felix.atomos.utils.core.plugins.ResourcePlugin;
import org.apache.felix.atomos.utils.core.plugins.activator.InvocatingBundleActivatorPlugin;
import org.apache.felix.atomos.utils.core.plugins.activator.ReflectionBundleActivatorPlugin;
import org.apache.felix.atomos.utils.core.plugins.collector.PathCollectorPlugin;
import org.apache.felix.atomos.utils.core.plugins.finaliser.ni.NativeImagePlugin;
import org.apache.felix.atomos.utils.core.plugins.index.IndexPlugin;
import org.junit.jupiter.api.BeforeAll;

public class TestBase
{

    @BeforeAll
    public static void setup() throws IOException
    {

        assertThat(getAllDependencys()).size().describedAs(
            "No Dependencys - Please run maven-phase 'generate-resources' first. './mvnw generate-resources -f atomos.maven/pom.xml'").isGreaterThan(
                0);
    }

    ContextImpl launch(List<Path> paths, Path tempDir) throws IOException
    {
        GlobalTestConfig cfg = fileConfig(FileType.ARTIFACT, paths, tempDir);

        //alphabetic order

        //        LauncherBuilderImpl lb = new LauncherBuilderImpl();
        Launcher l = Launcher.builder()//
            .addPlugin(ReflectionBundleActivatorPlugin.class, cfg)//
            .addPlugin(ComponentDescriptionPlugin.class, cfg)//
            .addPlugin(NativeImagePlugin.class, cfg)//
            .addPlugin(PathCollectorPlugin.class, cfg)//
            .addPlugin(GogoPlugin.class, cfg)//
            .addPlugin(IndexPlugin.class, cfg)//
            .addPlugin(InvocatingBundleActivatorPlugin.class, cfg)//
            .addPlugin(OsgiDTOPlugin.class, cfg)//
            .addPlugin(ResourcePlugin.class, cfg)//
            .build();

        ContextImpl contextImpl = new ContextImpl();
        l.execute(contextImpl);

        return contextImpl;
    }

    private GlobalTestConfig fileConfig(FileType fileType, List<Path> files,
        Path tempPath) throws IOException
    {
        GlobalTestConfig cfg = new GlobalTestConfig(fileType, files, tempPath);
        return cfg;
    }

}