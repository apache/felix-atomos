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

import static org.apache.felix.atomos.utils.core.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT;
import static org.apache.felix.atomos.utils.core.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL;

import java.nio.file.Path;
import java.util.List;

import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FullTest extends TestBase
{

    @Test
    void testDirectoryFileCollectorPlugin(@TempDir Path tempDir) throws Exception
    {

        List<Path> paths = TestConstants.getDependencys(
            DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT,
            DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL);

        ContextImpl context = launch(paths, tempDir);

        ReflectionConfiguration rcs = context.getReflectConfig();
        //        assertThat(testP).containsExactlyInAnyOrder(paths.toArray(Path[]::new));
        System.out.println(context);
    }

}