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

import static org.apache.felix.atomos.maven.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_REFLECT_COMMAND;
import static org.apache.felix.atomos.maven.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_REFLECT_DTO;
import static org.apache.felix.atomos.maven.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT;
import static org.apache.felix.atomos.maven.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL;
import static org.apache.felix.atomos.maven.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL_ACTIVATOR;
import static org.apache.felix.atomos.maven.TestConstants.DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_USER;
import static org.apache.felix.atomos.maven.TestConstants.DEP_ORG_OSGI_CORE;
import static org.apache.felix.atomos.maven.TestConstants.DEP_ORG_OSGI_DTO;
import static org.apache.felix.atomos.maven.TestConstants.filterConstructor;
import static org.apache.felix.atomos.maven.TestConstants.filterMethod;
import static org.apache.felix.atomos.maven.TestConstants.filterReflectConfigByClassName;
import static org.apache.felix.atomos.maven.TestConstants.getDependencys;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.felix.atomos.maven.reflect.ConstructorConfig;
import org.apache.felix.atomos.maven.reflect.MethodConfig;
import org.apache.felix.atomos.maven.reflect.ReflectConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ReflectConfigTest extends TestBase
{
    @Test
    void testJsonSimple() throws Exception
    {
        ReflectConfig rc = new ReflectConfig("a");
        String json = ReflectConfigUtil.json(rc);
        json = shrinkJson(json);
        assertEquals("{\"name\":\"a\"}", json);
    }

    @Test
    void testJson() throws Exception
    {
        ReflectConfig rc = new ReflectConfig("a");
        rc.fields.add("f1");
        rc.fields.add("f2");

        rc.constructor.add(new ConstructorConfig());
        rc.constructor.add(new ConstructorConfig(new String[] { "p1" }));

        rc.methods.add(new MethodConfig("m1", null));
        rc.methods.add(new MethodConfig("m2", new String[] { "m2p1" }));
        rc.methods.add(new MethodConfig("m3", new String[] { "m3p1", "m3p2" }));

        String json = ReflectConfigUtil.json(rc);
        json = shrinkJson(json);
        String exp = "{\"name\":\"a\",\"fields\":[{\"name\":\"f1\"},{\"name\":\"f2\"}],\"methods\":[{\"name\":\"<init>\",\"parameterTypes\":[]},{\"name\":\"<init>\",\"parameterTypes\":[\"p1\"]},{\"name\":\"m1\"},{\"name\":\"m2\",\"parameterTypes\":[\"m2p1\"]},{\"name\":\"m3\",\"parameterTypes\":[\"m3p1\",\"m3p2\"]}]}";
        assertEquals(exp, json);
    }


    private String shrinkJson(String json)
    {
        json = json.replace(" ", "").replace("\n", "");
        return json;
    }

    @Test
    void testJsonFull() throws Exception
    {
        List<ReflectConfig> rcs = new ArrayList<>();
        rcs.add(new ReflectConfig("z"));
        rcs.add(new ReflectConfig("a"));
        String json = ReflectConfigUtil.json(rcs);
        json = shrinkJson(json);
        assertEquals("[{\"name\":\"a\"},{\"name\":\"z\"}]", json);
    }

    @Test
    void testActivateMethod(@TempDir Path tempDir) throws Exception
    {
        List<Path> paths = getDependencys(DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT,
            DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL);
        List<ReflectConfig> rcs = ReflectConfigUtil.reflectConfig(paths);
        //System.out.println(ReflectConfigUtil.json(rcs));

        ReflectConfig rc = filterReflectConfigByClassName(rcs,
            "org.apache.felix.atomos.tests.testbundles.service.impl.EchoImpl");
        assertThat(rc.fields).isEmpty();

        Optional<ConstructorConfig> oc = filterConstructor(rc, new String[] {});
        assertTrue(oc.isPresent());

        Optional<MethodConfig> omc1 = filterMethod(rc, "activate", null);
        assertTrue(omc1.isPresent());
    }

    @Test
    void testReflectBundleActivator(@TempDir Path tempDir) throws Exception
    {
        List<Path> paths = getDependencys(DEP_ORG_OSGI_CORE,
            DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL_ACTIVATOR);

        List<ReflectConfig> list = ReflectConfigUtil.reflectConfig(paths);

        //System.out.println(ReflectConfigUtil.json(list));
        ReflectConfig rc = filterReflectConfigByClassName(list,
            "org.apache.felix.atomos.tests.testbundles.service.impl.activator.Activator");
        assertThat(rc.fields).isEmpty();

        Optional<ConstructorConfig> oc = filterConstructor(rc, new String[] {});
        assertTrue(oc.isPresent());

        Optional<MethodConfig> omc1 = filterMethod(rc, "start",
            new String[] { "org.osgi.framework.BundleContext" });
        assertTrue(omc1.isPresent());
        Optional<MethodConfig> omc2 = filterMethod(rc, "stop",
            new String[] { "org.osgi.framework.BundleContext" });
        assertTrue(omc2.isPresent());
    }

    @Test
    void testBundleActivatorMagic(@TempDir Path tempDir) throws Exception
    {
        List<Path> paths = getDependencys(DEP_ORG_OSGI_CORE,
            DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL_ACTIVATOR,
            DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT);

        List<ReflectConfig> rcs = ReflectConfigUtil.reflectConfig(paths);

        //System.out.println(ReflectConfigUtil.json(rcs));

        ReflectConfig rc = filterReflectConfigByClassName(rcs,
            "org.apache.felix.atomos.tests.testbundles.service.impl.activator.ActivatorEcho");
        assertThat(rc.constructor).isEmpty();
        assertThat(rc.fields).isEmpty();

        Optional<MethodConfig> omc1 = filterMethod(rc, "echo", null);
        assertTrue(omc1.isPresent());
    }

    @Test
    void testDTO(@TempDir Path tempDir) throws Exception
    {

        List<Path> paths = getDependencys(DEP_ATOMOS_TESTS_TESTBUNDLES_REFLECT_DTO,
            DEP_ORG_OSGI_DTO);

        List<ReflectConfig> rcs = ReflectConfigUtil.reflectConfig(paths);
        //System.out.println(ReflectConfigUtil.json(rcs));

        ReflectConfig rc = filterReflectConfigByClassName(rcs,
            "org.apache.felix.atomos.tests.testbundles.reflect.command.OneDTO");
        assertThat(rc.constructor).isEmpty();
        assertThat(rc.methods).isEmpty();

        assertThat(rc.fields).containsExactly("one");
    }

    @Test
    void testGOGOCommand(@TempDir Path tempDir) throws Exception
    {
        List<Path> paths = getDependencys(DEP_ATOMOS_TESTS_TESTBUNDLES_REFLECT_COMMAND);
        List<ReflectConfig> rcs = ReflectConfigUtil.reflectConfig(paths);
        //System.out.println(ReflectConfigUtil.json(rcs));

        ReflectConfig rc1 = filterReflectConfigByClassName(rcs,
            "org.apache.felix.atomos.tests.testbundles.reflect.command.AbstractCmd");
        assertThat(rc1.constructor).isEmpty();
        assertThat(rc1.fields).isEmpty();

        Optional<MethodConfig> rc1mc1 = filterMethod(rc1, "multiple", null);
        assertNotNull(rc1mc1);

        ReflectConfig rc2 = filterReflectConfigByClassName(rcs,
            "org.apache.felix.atomos.tests.testbundles.reflect.command.CmdExample");

        assertThat(rc2.fields).isEmpty();

        Optional<ConstructorConfig> rc2cc1 = filterConstructor(rc2, new String[] {});
        assertTrue(rc2cc1.isPresent());

        Optional<MethodConfig> rc2mc1 = filterMethod(rc2, "a", null);
        assertTrue(rc2mc1.isPresent());

        Optional<MethodConfig> rc2mc2 = filterMethod(rc2, "multiple", null);
        assertTrue(rc2mc2.isPresent());

        Optional<MethodConfig> rc2mc3 = filterMethod(rc2, "single", null);
        assertTrue(rc2mc3.isPresent());

    }

    @Test
    void testReference(@TempDir Path tempDir) throws Exception
    {
        List<Path> paths = getDependencys(DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT,
            DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_USER);
        List<ReflectConfig> rcs = ReflectConfigUtil.reflectConfig(paths);
        //System.out.println(ReflectConfigUtil.json(rcs));

        ReflectConfig rc1 = filterReflectConfigByClassName(rcs,
            "org.apache.felix.atomos.tests.testbundles.service.contract.Echo");
        assertThat(rc1.constructor).isEmpty();
        assertThat(rc1.fields).isEmpty();
        assertThat(rc1.methods).isEmpty();

        ReflectConfig rc2 = filterReflectConfigByClassName(rcs,
            "org.apache.felix.atomos.tests.testbundles.service.user.EchoUser");

        assertThat(rc2.fields).isEmpty();

        Optional<ConstructorConfig> rc2cc1 = filterConstructor(rc2, new String[] {});
        assertTrue(rc2cc1.isPresent());

        Optional<MethodConfig> rc2mc1 = filterMethod(rc2, "activate", null);
        assertTrue(rc2mc1.isPresent());

        Optional<MethodConfig> rc2mc2 = filterMethod(rc2, "setEcho", null);
        assertTrue(rc2mc2.isPresent());

        Optional<MethodConfig> rc2mc3 = filterMethod(rc2, "unsetEcho", null);
        assertTrue(rc2mc3.isPresent());

        ReflectConfig rc3 = filterReflectConfigByClassName(rcs,
            "org.apache.felix.atomos.tests.testbundles.service.user.EchoUser2");
        assertThat(rc3.fields).isEmpty();
        assertThat(rc3.methods).isEmpty();
        Optional<ConstructorConfig> rc3cc1 = filterConstructor(rc3,
            new String[] { "java.util.Map",
        "org.apache.felix.atomos.tests.testbundles.service.contract.Echo" });
        assertTrue(rc3cc1.isPresent());
    }
}