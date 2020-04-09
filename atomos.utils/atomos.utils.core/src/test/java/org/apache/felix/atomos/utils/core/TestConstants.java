/**
 *
 */
package org.apache.felix.atomos.utils.core;

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

import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionClassConfig;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConstructorConfig;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionMethodConfig;

public class TestConstants
{
    public static String ACTIVATOR_CONSTRUCTOR = "\"methods\":[{\"name\":\"<init>\",\"parameterTypes\":[] }]";
    public static String COMPONENT_CONSTRUCTOR = "\"allPublicConstructors\" : true";
    public static String DEP_ATOMOS_TESTS_TESTBUNDLES_REFLECT_COMMAND = "org.apache.felix.atomos.tests.testbundles.reflect.command-";
    public static String DEP_ATOMOS_TESTS_TESTBUNDLES_REFLECT_DTO = "org.apache.felix.atomos.tests.testbundles.reflect.dto-";
    public static String DEP_ATOMOS_TESTS_TESTBUNDLES_RESOURCE_A = "org.apache.felix.atomos.tests.testbundles.resource.a-";
    public static String DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_CONTRACT = "org.apache.felix.atomos.tests.testbundles.service.contract-";
    public static String DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL = "org.apache.felix.atomos.tests.testbundles.service.impl-";
    public static String DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_IMPL_ACTIVATOR = "org.apache.felix.atomos.tests.testbundles.service.impl.activator-";
    public static String DEP_ATOMOS_TESTS_TESTBUNDLES_SERVICE_USER = "org.apache.felix.atomos.tests.testbundles.service.user-";
    public static String DEP_FELIX_GOGO_COMMAND = "org.apache.felix.gogo.command-";
    public static String DEP_FELIX_GOGO_RUNTIME = "org.apache.felix.gogo.runtime-";
    public static String DEP_FELIX_HTTP_API = "org.apache.felix.http.api-";
    public static String DEP_FELIX_HTTP_SERVLET_API = "org.apache.felix.http.servlet-api-";
    public static String DEP_FELIX_SCR = "org.apache.felix.scr-";
    public static String DEP_FELIX_WEBCONSOLE = "org.apache.felix.webconsole-";
    public static String DEP_ORG_OSGI_DTO = "org.osgi.dto-";
    public static String DEP_ORG_OSGI_FRAMEWORK = "org.osgi.framework-";
    public static String DEP_ORG_OSGI_SERVICE_HTTP = "org.osgi.service.http-";
    public static String DEP_ORG_OSGI_SERVICE_LOG = "org.osgi.service.log-";

    public static Optional<ReflectionConstructorConfig> filterConstructor(
        ReflectionClassConfig reflectionClassConfig, String[] parameterTypes)
    {
        assertNotNull(reflectionClassConfig);
        assertNotNull(reflectionClassConfig.getConstructors());

        return reflectionClassConfig.getConstructors().stream().filter(
            c -> Arrays.equals(parameterTypes, c.getMethodParameterTypes())).findAny();

    }

    public static Optional<ReflectionMethodConfig> filterMethod(
        ReflectionClassConfig reflectionClassConfig, String name, String[] parameterTypes)
    {
        assertNotNull(reflectionClassConfig);
        assertNotNull(reflectionClassConfig.getMethods());

        return reflectionClassConfig.getMethods().stream().filter(
            c -> c.getName().equals(name)
            && Arrays.equals(parameterTypes, c.getMethodParameterTypes())).findAny();

    }

    public static ReflectionClassConfig filterReflectConfigByClassName(
        ReflectionConfiguration rc, String checkClass)
    {
        assertThat(rc).isNotNull();
        Optional<ReflectionClassConfig> optional = rc.getClassConfigs().stream().filter(
            c -> c.getClassName().equals(checkClass)).findFirst();
        assertTrue(optional.isPresent());
        return optional.get();
    }

    public static List<Path> getAllDependencys() throws IOException
    {
        return getAllDependencysFrom("target/test-dependencies/");
    }

    public static List<Path> getAllDependencysFrom(String dir) throws IOException
    {
        Path dirp = Paths.get(dir);

        if (!Files.exists(dirp))
        {
            return new ArrayList<>();
        }
        return Files.list(dirp).filter(p -> p.toString().endsWith(".jar")).collect(
            Collectors.toList());
    }

    public static Path getDependency(String depName) throws IOException
    {
        Path testDepsDir = Paths.get("target/test-dependencies/");
        List<Path> paths = Files.list(testDepsDir).filter(
            p -> p.getFileName().toString().startsWith(depName)).collect(
                Collectors.toList());
        assertEquals(1, paths.size(),
            String.format("Must be exact one test Dependency with the name %s", depName));
        return paths.get(0);
    }

    public static List<Path> getDependencys(String... depNames) throws IOException
    {
        List<Path> paths = new ArrayList<>();
        for (String depName : depNames)
        {
            paths.add(getDependency(depName));
        }
        return paths;
    }

}