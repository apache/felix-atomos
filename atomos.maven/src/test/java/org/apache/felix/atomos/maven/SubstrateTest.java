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

import static org.apache.felix.atomos.maven.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_RESOURCE_A;
import static org.apache.felix.atomos.maven.TestConstants.getDependency;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SubstrateTest extends TestBase
{

    @Test
    void testSubstrate(@TempDir Path tempDir) throws Exception
    {
        Path path = getDependency(DEP_ATOMOS_TESTS_TESTBUNDLES_RESOURCE_A);
        Path atomosSubstrateJar = SubstrateUtil.substrate(Arrays.asList(path), tempDir);
        assertThat(atomosSubstrateJar).exists().isRegularFile();

        try (JarFile jarFile = new JarFile(atomosSubstrateJar.toFile());)
        {
            assertThat(jarFile.stream().map(JarEntry::getName).collect(
                Collectors.toList())).containsOnly("META-INF/MANIFEST.MF", //
                    "atomos/0/META-INF/MANIFEST.MF", //
                    "atomos/0/META-TEXT/file.txt", //
                    "atomos/0/org/apache/felix/atomos/tests/testbundles/resource/a/file.txt", //
                    "atomos/0/file.txt", //
                    "atomos/bundles.index", //
                    "META-INF/native-image/resource-config.json");
        }

    }
}