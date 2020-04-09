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
package org.apache.felix.atomos.utils.core.plugins;

import static org.apache.felix.atomos.utils.core.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_RESOURCE_A;
import static org.apache.felix.atomos.utils.core.TestConstants.getDependency;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.api.PluginConfigBase;
import org.apache.felix.atomos.utils.core.TestBase;
import org.apache.felix.atomos.utils.core.TestContext;
import org.apache.felix.atomos.utils.core.plugins.index.IndexPlugin;
import org.apache.felix.atomos.utils.core.plugins.index.IndexPluginConfig;
import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultResourceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SubstrateTest extends TestBase
{
    private final List<String> resourceIndexItemsList = List.of(
        "atomos/0/META-INF/MANIFEST.MF", //
        "atomos/0/META-TEXT/file.txt", //
        "atomos/0/org/apache/felix/atomos/tests/testbundles/resource/a/file.txt", //
        "atomos/0/file.txt");
    private final List<String> resourceIndexMetaList = List.of("META-INF/MANIFEST.MF", //
        "META-INF/native-image/resource-config.json");

    @Test
    void testSubstrate(@TempDir Path tempDir) throws Exception
    {
        Path path = getDependency(DEP_ATOMOS_TESTS_TESTBUNDLES_RESOURCE_A);
        IndexPlugin i = new IndexPlugin();
        i.init(new IndexPluginConfig()
        {

            @Override
            public Path indexOutputDirectory()
            {
                return tempDir;
            }
        });

        JarFile jar = new JarFile(path.toFile());
        URLClassLoader classLoader = new URLClassLoader(
            new URL[] { path.toUri().toURL() });

        DefaultResourceConfiguration resourceConfig = new DefaultResourceConfiguration();
        TestContext testContext = new TestContext()
        {

            @Override
            public void addFile(Path path, FileType type)
            {
                assertEquals(FileType.ARTIFACT, type);
                assertThat(path.toString()).endsWith("atomos.substrate.jar");
                assertThat(path).exists().isRegularFile();

                Exception catchedException = null;
                try (JarFile jarFile = new JarFile(path.toFile());)
                {
                    assertThat(jarFile.stream().map(JarEntry::getName).collect(
                        Collectors.toList())).containsAll(
                            resourceIndexItemsList).containsAll(
                                resourceIndexMetaList).contains(
                                    IndexPlugin.ATOMOS_BUNDLES_INDEX);
                }
                catch (IOException e)
                {
                    catchedException = e;
                }
                assertNull(catchedException);
            }

            @Override
            public void addResourceConfig(ResourceConfiguration rc)
            {
                assertTrue(rc.getResourceBundles().isEmpty());
                assertTrue(rc.getResourcePackages().isEmpty());
                resourceConfig.addResourcePattern(rc.getResourcePatterns());
            }
        };

        i.preJars(testContext);
        i.doJar(jar, testContext, classLoader);
        i.postJars(testContext);

        assertThat(resourceConfig.getResourcePatterns()).containsAll(
            resourceIndexItemsList).doesNotContainSequence(
                resourceIndexMetaList).contains(
                    IndexPlugin.ATOMOS_BUNDLES_INDEX).contains(
                        IndexPlugin.ATOMOS_CATH_ALL);
    }
}