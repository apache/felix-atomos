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

import static org.apache.felix.atomos.utils.core.TestConstants.DEP_FELIX_HTTP_SERVLET_API;
import static org.apache.felix.atomos.utils.core.TestConstants.DEP_FELIX_WEBCONSOLE;
import static org.apache.felix.atomos.utils.core.TestConstants.DEP_ORG_OSGI_FRAMEWORK;
import static org.apache.felix.atomos.utils.core.TestConstants.DEP_ORG_OSGI_SERVICE_HTTP;
import static org.apache.felix.atomos.utils.core.TestConstants.DEP_ORG_OSGI_SERVICE_LOG;
import static org.apache.felix.atomos.utils.core.TestConstants.getDependencys;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MojoTestExperiments extends TestBase
{

    @Test
    void testReflectBundleActivatorMagicTests(@TempDir Path tempDir) throws Exception
    {
        List<Path> paths = getDependencys(DEP_FELIX_WEBCONSOLE, DEP_ORG_OSGI_SERVICE_LOG,
            //            DEP_FELIX_GOGO_RUNTIME, DEP_FELIX_GOGO_COMMAND, DEP_FELIX_SCR,
            DEP_ORG_OSGI_FRAMEWORK, DEP_FELIX_HTTP_SERVLET_API,
            DEP_ORG_OSGI_SERVICE_HTTP);

        //   List<ReflectConfig> rcs = ReflectConfigUtil.reflectConfig(paths);

        //        System.out.println(ReflectConfigUtil.json(rcs));

    }

}