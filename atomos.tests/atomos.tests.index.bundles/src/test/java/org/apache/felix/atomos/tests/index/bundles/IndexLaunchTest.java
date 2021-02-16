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
package org.apache.felix.atomos.tests.index.bundles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.felix.atomos.Atomos;
import org.apache.felix.atomos.AtomosContent;
import org.apache.felix.atomos.AtomosLayer;
import org.apache.felix.atomos.impl.base.AtomosBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleWiring;

public class IndexLaunchTest
{

    private static final String TESTBUNDLES_SERVICE_IMPL = "org.apache.felix.atomos.tests.testbundles.service.impl";
    private static final String INDEX_BSN_PREFIX = "bundle.";

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

    private Atomos getRuntime(BundleContext bc)
    {
        ServiceReference<Atomos> ref = bc.getServiceReference(Atomos.class);
        assertNotNull(ref, "No reference found.");
        Atomos runtime = bc.getService(ref);
        assertNotNull(runtime, "No service found.");
        return runtime;
    }

    private Framework getTestFramework(Path storage, String indexPath)
        throws BundleException
    {
        Framework f;
        if (indexPath == null)
        {
            f = Atomos.newAtomos().newFramework(
                Map.of(Constants.FRAMEWORK_STORAGE, storage.toFile().getAbsolutePath()));
        }
        else
        {
            Map<String, String> config = Map.of(Constants.FRAMEWORK_STORAGE,
                storage.toFile().getAbsolutePath(),
                AtomosBase.ATOMOS_INDEX_PATH_PROP, indexPath);
            f = Atomos.newAtomos(config).newFramework(config);
        }
        f.start();
        return f;
    }

    @Test
    void testIgnoreIndex(@TempDir Path storage) throws BundleException
    {
        testFramework = getTestFramework(storage, AtomosBase.ATOMOS_IGNORE_INDEX);
        BundleContext bc = testFramework.getBundleContext();

        Atomos runtime = getRuntime(bc);
        assertFindBundle("java.base", runtime.getBootLayer(), runtime.getBootLayer(),
            true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL, runtime.getBootLayer(),
            runtime.getBootLayer(), true);

        // the default indexed bundles should not be found
        for (int i = 1; i <= 4; i++)
        {
            assertFindBundle(INDEX_BSN_PREFIX + i, runtime.getBootLayer(),
                runtime.getBootLayer(), false);
        }
    }

    @Test
    void testFindBundleDefault(@TempDir Path storage) throws BundleException
    {
        doTestFindBundle(storage, null, List.of(1, 2, 3, 4), List.of());
    }

    @Test
    void testFindBundleTestIndex(@TempDir Path storage) throws BundleException
    {
        doTestFindBundle(storage, "/testIndex/test.index", List.of(3, 4), List.of(1, 2));
    }

    void doTestFindBundle(@TempDir Path storage, String indexPath,
        Collection<Integer> expected,
        Collection<Integer> unexpected)
        throws BundleException
    {
        testFramework = getTestFramework(storage, indexPath);
        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        Atomos runtime = getRuntime(bc);
        assertFindBundle("java.base", runtime.getBootLayer(), runtime.getBootLayer(),
            true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL, runtime.getBootLayer(),
            runtime.getBootLayer(), true);

        for (int i : expected)
        {
            assertFindBundle(INDEX_BSN_PREFIX + i, runtime.getBootLayer(),
                runtime.getBootLayer(), true);
        }
        for (int i : unexpected)
        {
            assertFindBundle(INDEX_BSN_PREFIX + i, runtime.getBootLayer(),
                runtime.getBootLayer(), false);
        }
        assertFindBundle("not.found", runtime.getBootLayer(), null, false);
    }

    @Test
    void testActivatorServiceDefault(
        @TempDir Path storage)
        throws BundleException, InvalidSyntaxException
    {
        doTestActivatorService(storage, null, 1, 2, 3, 4);
    }

    @Test
    void testActivatorServiceTestIndex(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException
    {
        doTestActivatorService(storage, "testIndex/test.index", 3, 4);
    }

    void doTestActivatorService(@TempDir Path storage, String indexPath,
        int... expected)
        throws BundleException, InvalidSyntaxException
    {
        testFramework = getTestFramework(storage, indexPath);
        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        for (int i : expected)
        {
            assertFindActivatorService(bc, i);
        }
    }

    private void assertFindActivatorService(BundleContext bc, int i)
        throws InvalidSyntaxException
    {
        Collection<ServiceReference<BundleActivator>> activatorRefs = bc.getServiceReferences(
            BundleActivator.class, "(test.activator=ActivatorBundle" + i + ")");
        assertEquals(1, activatorRefs.size(), "Wrong number of services for: " + i);
        ServiceReference<BundleActivator> ref = activatorRefs.iterator().next();
        assertEquals(ref.getBundle(), ref.getProperty("test.bundle"), "Wrong bundle.");
    }

    @Test
    void testGetEntryDefault(@TempDir Path storage) throws BundleException, IOException
    {
        doTestGetEntry(storage, null, 1, 2, 3, 4);
    }

    @Test
    void testGetEntryTestIndex(@TempDir Path storage) throws BundleException, IOException
    {
        doTestGetEntry(storage, "testIndex/test.index", 3, 4);
    }

    void doTestGetEntry(@TempDir Path storage, String indexPath,
        int... expected)
        throws BundleException, IOException
    {
        testFramework = getTestFramework(storage, indexPath);
        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        Atomos runtime = getRuntime(bc);
        Bundle b = assertFindBundle(TESTBUNDLES_SERVICE_IMPL,
            runtime.getBootLayer(),
            runtime.getBootLayer(), true).getBundle();
        assertNotNull(b, "No bundle found.");
        URL mf = b.getEntry("/META-INF/MANIFEST.MF");
        assertNotNull(mf, "No manifest found.");

        for (int i : expected)
        {
            AtomosContent content = runtime.getBootLayer().findAtomosContent(
                "bundle." + i).get();
            Bundle bundle = content.getBundle();
            URL commonURL = bundle.getEntry("OSGI-INF/common.txt");
            assertNotNull(commonURL, "No common url found: " + i);
            assertContent(Integer.toString(i), commonURL);
            URL bundleResource1 = bundle.getEntry("OSGI-INF/bundle." + i + "-1.txt");
            assertNotNull(bundleResource1, "No bundle resource 1 found: " + i);
            assertContent(Integer.toString(i), bundleResource1);
            URL bundleResource2 = bundle.getEntry("OSGI-INF/bundle." + i + "-2.txt");
            assertNotNull(bundleResource2, "No bundle resource 2 found: " + i);
            assertContent(Integer.toString(i), bundleResource2);
            assertNull(bundle.getEntry("OSGI-INF/notFound.txt"),
                "Found unexpected resource.");
        }
    }

    @Test
    void testFindEntriesDefault(@TempDir Path storage) throws BundleException, IOException
    {
        doTestFindEntries(storage, null, 1, 2, 3, 4);
    }

    @Test
    void testFindEntriesTestIndex(
        @TempDir Path storage)
        throws BundleException, IOException
    {
        doTestFindEntries(storage, "testIndex/test.index", 3, 4);
    }

    void doTestFindEntries(@TempDir Path storage, String indexPath,
        int... expected)
        throws BundleException, IOException
    {
        testFramework = getTestFramework(storage, indexPath);
        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        Atomos runtime = getRuntime(bc);

        for (int i : expected)
        {
            AtomosContent content = runtime.getBootLayer().findAtomosContent(
                "bundle." + i).get();
            List<String> expectedEntries = List.of( //
                "/META-INF/", //
                "/META-INF/MANIFEST.MF", //
                "/OSGI-INF/", //
                "/OSGI-INF/common.txt", //
                "/OSGI-INF/bundle." + i + "-1.txt", //
                "/OSGI-INF/bundle." + i + "-2.txt", //
                "/org/", //
                "/org/apache/", //
                "/org/apache/felix/", //
                "/org/apache/felix/atomos/", //
                "/org/apache/felix/atomos/tests/", //
                "/org/apache/felix/atomos/tests/index/", //
                "/org/apache/felix/atomos/tests/index/bundles/", //
                "/org/apache/felix/atomos/tests/index/bundles/b" + i + "/", //
                "/org/apache/felix/atomos/tests/index/bundles/b" + i + "/ActivatorBundle" + i + ".class" //
            );
            Bundle bundle = content.getBundle();
            BundleWiring wiring = bundle.adapt(BundleWiring.class);
            List<URL> entryURLs = wiring.findEntries("/", "*",
                BundleWiring.FINDENTRIES_RECURSE);
            List<String> actualEntries = entryURLs.stream().map(
                URL::getPath).collect(Collectors.toList());

            assertEquals(expectedEntries, actualEntries, "Wrong list of entries.");

            expectedEntries.forEach(
                e -> assertNotNull(bundle.getEntry(e), "No entry found: " + e));
        }
    }

    private void assertContent(String expected, URL url) throws IOException
    {
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(url.openStream())))
        {
            assertEquals(expected, br.readLine(), "Wrong content for: " + url);
        }
    }

    private AtomosContent assertFindBundle(String name, AtomosLayer layer,
        AtomosLayer expectedLayer, boolean expectedToFind)
    {
        Optional<AtomosContent> result = layer.findAtomosContent(name);
        if (expectedToFind)
        {
            assertTrue(result.isPresent(), "Could not find bundle: " + name);
            assertEquals(name, result.get().getSymbolicName(), "Wrong name");
            assertEquals(expectedLayer, result.get().getAtomosLayer(),
                "Wrong layer for bundle: " + name);
            Bundle b = result.get().getBundle();
            assertNotNull(b, "No Bundle.");
            assertEquals(name, b.getSymbolicName(), "Wrong BSN");
        }
        else
        {
            assertFalse(result.isPresent(), "Found unexpected bundle: " + name);
        }
        return result.orElse(null);
    }

    @Test
    void testBundleWithCustomHeader(@TempDir Path storage) throws BundleException
    {
        testFramework = Atomos.newAtomos((location, headers) -> {
            headers = new HashMap<>(headers);
            headers.put("X-TEST", location);
            return Optional.of(headers);
        }).newFramework(
            Map.of(Constants.FRAMEWORK_STORAGE, storage.toFile().getAbsolutePath()));
        testFramework.start();
        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        Atomos runtime = getRuntime(bc);
        runtime.getBootLayer().getAtomosContents().forEach(c -> {
            if (c.getBundle().getBundleId() == 0)
            {
                return;
            }
            String customHeader = c.getBundle().getHeaders(null).get("X-TEST");
            assertEquals(c.getAtomosLocation(), customHeader, "Wrong header value");
        });
    }
}
