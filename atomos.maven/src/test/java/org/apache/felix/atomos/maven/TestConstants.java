/**
 *
 */
package org.apache.felix.atomos.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.felix.atomos.maven.reflect.ConstructorConfig;
import org.apache.felix.atomos.maven.reflect.MethodConfig;
import org.apache.felix.atomos.maven.reflect.ReflectConfig;

public class TestConstants
{

    static String ACTIVATOR_CONSTRUCTOR = "\"methods\":[{\"name\":\"<init>\",\"parameterTypes\":[] }]";
    static String COMPONENT_CONSTRUCTOR = "\"allPublicConstructors\" : true";
    static String DEP_ATOMOS_TESTS_TESTBUNDLES_REFLECT_COMMAND = "org.apache.felix.atomos.tests.testbundles.reflect.command-";
    static String DEP_ATOMOS_TESTS_TESTBUNDLES_REFLECT_DTO = "org.apache.felix.atomos.tests.testbundles.reflect.dto-";
    static String DEP_ATOMOS_TESTS_TESTBUNDLES_RESOURCE_A = "org.apache.felix.atomos.tests.testbundles.resource.a-";
    static String DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT = "org.apache.felix.atomos.tests.testbundles.service.contract-";
    static String DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL = "org.apache.felix.atomos.tests.testbundles.service.impl-";
    static String DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL_ACTIVATOR = "org.apache.felix.atomos.tests.testbundles.service.impl.activator-";
    static String DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_USER = "org.apache.felix.atomos.tests.testbundles.service.user-";
    static String DEP_FELIX_GOGO_COMMAND = "org.apache.felix.gogo.command-";
    static String DEP_FELIX_GOGO_RUNTIME = "org.apache.felix.gogo.runtime-";
    static String DEP_FELIX_HTTP_API = "org.apache.felix.http.api-";
    static String DEP_FELIX_HTTP_SERVLET_API = "org.apache.felix.http.servlet-api-";
    static String DEP_FELIX_SCR = "org.apache.felix.scr-";
    static String DEP_FELIX_WEBCONSOLE = "org.apache.felix.webconsole-";
    static String DEP_ORG_OSGI_CORE = "org.osgi.core-";
    static String DEP_ORG_OSGI_DTO = "org.osgi.dto-";
    static String DEP_ORG_OSGI_SERVICE_HTTP = "org.osgi.service.http-";
    static String DEP_ORG_OSGI_SERVICE_LOG = "org.osgi.service.log-";

    static Optional<ConstructorConfig> filterConstructor(ReflectConfig reflectConfig,
        String[] parameterTypes)
    {
        assertNotNull(reflectConfig);
        assertNotNull(reflectConfig.constructor);

        return reflectConfig.constructor.stream().filter(
            c -> Arrays.equals(parameterTypes, c.methodParameterTypes)).findAny();

    }

    static Optional<MethodConfig> filterMethod(ReflectConfig reflectConfig, String name,
        String[] parameterTypes)
    {
        assertNotNull(reflectConfig);
        assertNotNull(reflectConfig.methods);

        return reflectConfig.methods.stream().filter(c -> c.name.equals(name)
            && Arrays.equals(parameterTypes, c.methodParameterTypes)).findAny();

    }

    static ReflectConfig filterReflectConfigByClassName(List<ReflectConfig> list,
        String checkClass)
    {
        assertThat(list).isNotNull();
        Optional<ReflectConfig> optional = list.stream().filter(
            c -> c.className.equals(checkClass)).findFirst();
        assertTrue(optional.isPresent());
        return optional.get();
    }

    static List<Path> getAllDependencys() throws IOException
    {
        return getAllDependencysFrom("target/test-dependencies/");
    }

    static List<Path> getAllDependencysFrom(String dir) throws IOException
    {
        Path dirp = Paths.get(dir);

        if (!Files.exists(dirp))
        {
            return new ArrayList<>();
        }
        return Files.list(dirp).filter(p -> p.toString().endsWith(".jar")).collect(
            Collectors.toList());
    }

    static Path getDependency(String depName) throws IOException
    {
        Path testDepsDir = Paths.get("target/test-dependencies/");
        List<Path> paths = Files.list(testDepsDir).filter(
            p -> p.getFileName().toString().startsWith(depName)).collect(
                Collectors.toList());
        assertEquals(1, paths.size(),
            String.format("Must be exact one test Dependency with the name %s", depName));
        return paths.get(0);
    }

    static List<Path> getDependencys(String... depNames) throws IOException
    {
        List<Path> paths = new ArrayList<>();
        for (String depName : depNames)
        {
            paths.add(getDependency(depName));
        }
        return paths;
    }

}