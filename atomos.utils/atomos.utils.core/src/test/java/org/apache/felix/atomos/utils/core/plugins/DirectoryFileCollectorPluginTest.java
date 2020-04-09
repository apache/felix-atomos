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

import static org.apache.felix.atomos.utils.core.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT;
import static org.apache.felix.atomos.utils.core.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.core.TestBase;
import org.apache.felix.atomos.utils.core.TestConstants;
import org.apache.felix.atomos.utils.core.TestContext;
import org.apache.felix.atomos.utils.core.plugins.collector.PathCollectorPlugin;
import org.apache.felix.atomos.utils.core.plugins.collector.PathCollectorPluginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DirectoryFileCollectorPluginTest extends TestBase
{

    @Test
    void testDirectoryFileCollectorPlugin(@TempDir Path tempDir) throws Exception
    {

        List<Path> paths = TestConstants.getDependencys(
            DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT,
            DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL);

        assertEquals(2, paths.size());
        PathCollectorPlugin dfp = new PathCollectorPlugin();

        dfp.init(new PathCollectorPluginConfig()
        {


            @Override
            public List<Path> paths()
            {
                return paths;
            }

            @Override
            public List<String> filters()
            {
                return null;
            }

            @Override
            public FileType fileType()
            {
                return FileType.ARTIFACT;
            }
        });

        List<Path> testP = Collections.synchronizedList(new ArrayList<>());
        dfp.collectFiles(new TestContext()
        {
            @Override
            public void addFile(Path path, FileType type)
            {

                testP.add(path);
                assertEquals(FileType.ARTIFACT, type);
            }

        });
        assertThat(testP).containsExactlyInAnyOrder(paths.toArray(Path[]::new));

    }

}