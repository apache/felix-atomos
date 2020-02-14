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
package org.apache.felix.atomos.tests.classpath.service.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.apache.felix.atomos.impl.runtime.base.AtomosCommands;
import org.apache.felix.atomos.runtime.AtomosBundleInfo;
import org.apache.felix.atomos.runtime.AtomosLayer;
import org.apache.felix.atomos.runtime.AtomosRuntime;
import org.apache.felix.atomos.runtime.AtomosRuntime.LoaderType;
import org.apache.felix.atomos.tests.classpath.service.ClasspathLaunch;
import org.apache.felix.atomos.tests.testbundles.service.contract.Echo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;

public class ClasspathLaunchTest
{

    private static final String TESTBUNDLES_SERVICE_IMPL = "org.apache.felix.atomos.tests.testbundles.service.impl";

    private static final String TESTBUNDLES_SERVICE_IMPL_A = "org.apache.felix.atomos.tests.testbundles.service.impl.a";
    private Framework testFramework;

    @AfterEach
    void afterTest() throws BundleException, InterruptedException, IOException
    {
        if (testFramework != null && testFramework.getState() == Bundle.ACTIVE)
        {
            testFramework.stop();
            testFramework.waitForStop(10000);
        }
    }

    @Test
    void testClassPathGogo(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException
    {

        ClasspathLaunch.main(new String[] {
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath() });
        testFramework = ClasspathLaunch.getFramework();
        BundleContext bc = testFramework.getBundleContext();

        String filter = "(osgi.command.scope=atomos)";

        Collection<ServiceReference<AtomosCommands>> serviceReferences = bc.getServiceReferences(
            AtomosCommands.class, filter);

        assertEquals(1, serviceReferences.size());
        AtomosCommands ac = bc.getService(serviceReferences.iterator().next());
        assertNotNull(ac, "AtomosCommands required");
        ac.list();

    }

    @Test
    void testClassPathServices(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException
    {
        ClasspathLaunch.main(new String[] {
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath() });
        testFramework = ClasspathLaunch.getFramework();
        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        checkBundleStates(bc.getBundles());

        checkServices(bc, 4);
        AtomosRuntime runtime = getRuntime(bc);
        assertNull(runtime.getBootLayer().adapt(ModuleLayer.class).orElse(null),
            "Found a ModuleLayer.");
    }

    private AtomosRuntime getRuntime(BundleContext bc)
    {
        ServiceReference<AtomosRuntime> ref = bc.getServiceReference(AtomosRuntime.class);
        assertNotNull(ref, "No reference found.");
        AtomosRuntime runtime = bc.getService(ref);
        assertNotNull(runtime, "No service found.");
        return runtime;
    }

    @Test
    void testInvalidCreateLayer(@TempDir Path storage) throws BundleException
    {
        AtomosRuntime runtime = AtomosRuntime.newAtomosRuntime();
        try
        {
            runtime.getBootLayer().addLayer("invalid", LoaderType.OSGI, storage);
            fail("Expected exception when addLayer is called.");
        }
        catch (UnsupportedOperationException e)
        {
            // expected
        }
    }

    @Test
    void testFindBundle(@TempDir Path storage) throws BundleException
    {
        ClasspathLaunch.main(new String[] {
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath() });
        testFramework = ClasspathLaunch.getFramework();
        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        AtomosRuntime runtime = getRuntime(bc);
        assertFindBundle("java.base", runtime.getBootLayer(), runtime.getBootLayer(),
            true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL, runtime.getBootLayer(),
            runtime.getBootLayer(), true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL_A, runtime.getBootLayer(),
            runtime.getBootLayer(), true);
        assertFindBundle("not.found", runtime.getBootLayer(), null, false);
    }

    @Test
    void testGetEntry(@TempDir Path storage) throws BundleException
    {
        ClasspathLaunch.main(new String[] {
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath() });
        testFramework = ClasspathLaunch.getFramework();
        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        AtomosRuntime runtime = getRuntime(bc);
        Bundle b = runtime.getBundle(
            assertFindBundle(TESTBUNDLES_SERVICE_IMPL_A,
                runtime.getBootLayer(), runtime.getBootLayer(), true));
        assertNotNull(b, "No bundle found.");
        URL mf = b.getEntry("/META-INF/MANIFEST.MF");
        assertNotNull(mf, "No manifest found.");
        mf = b.getEntry("META-INF/MANIFEST.MF");
        assertNotNull(mf, "No manifest found.");
    }

    private AtomosBundleInfo assertFindBundle(String name, AtomosLayer layer,
        AtomosLayer expectedLayer, boolean expectedToFind)
    {
        Optional<AtomosBundleInfo> result = layer.findAtomosBundle(name);
        if (expectedToFind)
        {
            assertTrue(result.isPresent(), "Could not find bundle: " + name);
            assertEquals(name, result.get().getSymbolicName(), "Wrong name");
            assertEquals(expectedLayer, result.get().getAtomosLayer(),
                "Wrong layer for bundle: " + name);
        }
        else
        {
            assertFalse(result.isPresent(), "Found unexpected bundle: " + name);
        }
        return result.orElse(null);
    }

    private void checkServices(BundleContext bc, int expectedNumber)
        throws InvalidSyntaxException
    {
        ServiceReference<?>[] echoRefs = bc.getAllServiceReferences(Echo.class.getName(),
            null);
        assertNotNull(echoRefs, "No Echo service ref found.");
        assertEquals(expectedNumber, echoRefs.length, "Wrong number of services.");
        for (ServiceReference<?> ref : echoRefs)
        {
            Echo echo = (Echo) bc.getService(ref);
            assertNotNull(echo, "No Echo service found.");
            assertEquals(ref.getProperty("type") + " Hello!!", echo.echo("Hello!!"),
                "Wrong Echo.");
            checkClassBundle(echo, ref);
        }
    }

    private void checkClassBundle(Object service, ServiceReference<?> ref)
    {
        Bundle b = FrameworkUtil.getBundle(service.getClass());
        assertEquals(ref.getBundle(), b, "Wrong bundle.");
    }

    private String getState(Bundle b)
    {
        switch (b.getState())
        {
            case Bundle.UNINSTALLED:
                return "UNINSTALLED";
            case Bundle.INSTALLED:
                return "INSTALLED";
            case Bundle.RESOLVED:
                return "RESOLVED";
            case Bundle.STARTING:
                return "STARTING";
            case Bundle.ACTIVE:
                return "ACTIVE";
            case Bundle.STOPPING:
                return "STOPPING";
            default:
                return "unknown";
        }
    }

    private void checkBundleStates(Bundle[] bundles)
    {
        assertTrue(bundles.length > 0, "No bundles: " + Arrays.toString(bundles));
        for (Bundle b : bundles)
        {
            String msg = b.getBundleId() + " " + b.getLocation() + ": "
                + b.getSymbolicName() + ": " + getState(b);
            System.out.println(msg);
            int expected;
            if ("osgi.annotation".equals(b.getSymbolicName())
                || "org.osgi.service.component.annotations".equals(b.getSymbolicName())
                || b.getSymbolicName().startsWith("org.eclipse.jdt.junit"))
            {
                expected = Bundle.INSTALLED;
            }
            else
            {
                expected = Bundle.ACTIVE;
            }
            if (b.getState() != expected && expected == Bundle.ACTIVE)
            {
                // for debugging
                try
                {
                    b.start();
                }
                catch (Throwable t)
                {
                    t.printStackTrace();
                }
            }
            assertEquals(expected, b.getState(), "Wrong bundle state for bundle: " + msg);
        }
    }
}
