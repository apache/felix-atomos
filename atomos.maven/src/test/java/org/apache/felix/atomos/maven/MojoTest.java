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

import static org.apache.felix.atomos.maven.TestConstants.getAllDependencys;

import java.nio.file.Path;
import java.util.List;

import org.apache.felix.atomos.maven.ResourceConfigUtil.ResourceConfigResult;
import org.apache.felix.atomos.maven.reflect.ReflectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MojoTest extends TestBase
{

    @Test
    void testFull(@TempDir Path tempDir) throws Exception
    {

        List<Path> paths = getAllDependencys();

        SubstrateUtil.indexContent(paths, tempDir);
        List<ReflectConfig> reflectConfigs = ReflectConfigUtil.reflectConfig(paths);
        ResourceConfigResult resourceConfigResult = ResourceConfigUtil.resourceConfig(
            paths);

        List<String> args = NativeImageBuilder.createExecutionArgs(List.of(), List.of(),
            List.of(), List.of(), resourceConfigResult, true, "", "");
        //        for (String arg : args)
        //        {
        //            System.out.println(arg);
        //
        //        }
        //
    }
}