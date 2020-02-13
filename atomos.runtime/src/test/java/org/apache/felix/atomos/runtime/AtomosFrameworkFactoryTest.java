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
package org.apache.felix.atomos.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.felix.atomos.runtime.AtomosRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.Framework;

public class AtomosFrameworkFactoryTest
{

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
    void testFactory(@TempDir Path storage) throws BundleException
    {
        ServiceLoader<ConnectFrameworkFactory> loader = ServiceLoader.load(
            getClass().getModule().getLayer(), ConnectFrameworkFactory.class);
        assertNotNull(loader, "null loader.");

        List<ConnectFrameworkFactory> factories = new ArrayList<>();
        loader.forEach((f) -> factories.add(f));
        assertFalse(factories.isEmpty(), "No factory found.");

        ConnectFrameworkFactory factory = factories.get(0);
        assertNotNull(factory, "null factory.");

        Map<String, String> config = Map.of(Constants.FRAMEWORK_STORAGE,
            storage.toFile().getAbsolutePath());
        testFramework = factory.newFramework(config,
            AtomosRuntime.newAtomosRuntime().newModuleConnector());
        doTestFramework(testFramework);
    }

    @Test
    void testRuntime(@TempDir Path storage) throws BundleException
    {
        AtomosRuntime runtime = AtomosRuntime.newAtomosRuntime();
        Map<String, String> config = Map.of(Constants.FRAMEWORK_STORAGE,
            storage.toFile().getAbsolutePath());
        testFramework = runtime.newFramework(config);
        doTestFramework(testFramework);
    }

    private void doTestFramework(Framework testFramework) throws BundleException
    {
        testFramework.start();
        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        Bundle[] bundles = bc.getBundles();

        assertEquals(ModuleLayer.boot().modules().size(), bundles.length,
            "Wrong number of bundles.");

        for (Bundle b : bundles)
        {
            String msg = b.getLocation() + ": " + b.getSymbolicName() + ": "
                + getState(b);
            System.out.println(msg);
            int expected;
            if ("osgi.annotation".equals(b.getSymbolicName()))
            {
                expected = Bundle.INSTALLED;
            }
            else
            {
                expected = Bundle.ACTIVE;
            }
            assertEquals(expected, b.getState(), "Wrong bundle state for bundle: " + msg);
        }
        Bundle javaLang = FrameworkUtil.getBundle(String.class);
        assertNotNull(javaLang, "No bundle found.");
        assertEquals(String.class.getModule().getName(), javaLang.getSymbolicName(),
            "Wrong bundle name.");
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

}
