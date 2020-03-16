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
package org.apache.felix.atomos.tests.modulepath.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.felix.atomos.launch.AtomosLauncher;
import org.apache.felix.atomos.runtime.AtomosContent;
import org.apache.felix.atomos.runtime.AtomosLayer;
import org.apache.felix.atomos.runtime.AtomosLayer.LoaderType;
import org.apache.felix.atomos.runtime.AtomosRuntime;
import org.apache.felix.atomos.tests.testbundles.service.contract.Echo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;

public class ModulepathLaunchTest
{

    /**
     *
     */
    private static final String ATOMOS_VERSION = "0.0.1-SNAPSHOT";
    private static final String TESTBUNDLES_SERVICE_IMPL = "org.apache.felix.atomos.tests.testbundles.service.impl";
    private static final String TESTBUNDLES_SERVICE_IMPL_A = "org.apache.felix.atomos.tests.testbundles.service.impl.a";
    private static final String TESTBUNDLES_SERVICE_IMPL_B = "org.apache.felix.atomos.tests.testbundles.service.impl.b";
    private static final String TESTBUNDLES_SERVICE_LIBRARY = "org.apache.felix.atomos.tests.testbundles.service.library";
    private static final String TESTBUNDLES_SERVICE_USER = "org.apache.felix.atomos.tests.testbundles.service.user";
    private static final String TESTBUNDLES_RESOURCE_A = "org.apache.felix.atomos.tests.testbundles.resource.a";
    /**
     *
     */
    private static final String RESSOURCE_A_CLAZZ_NAME = TESTBUNDLES_RESOURCE_A + ".Clazz";
    private static final String ATOMOS_DEBUG_PROP = "atomos.enable.debug";
    private Framework testFramework;

    @AfterEach
    void afterTest() throws BundleException, InterruptedException, IOException
    {
        if (testFramework != null && testFramework.getState() == Bundle.ACTIVE)
        {
            testFramework.stop();
            testFramework.waitForStop(10000);
        }
        System.getProperties().remove(ATOMOS_DEBUG_PROP);
    }

    private AtomosContent assertFindBundle(String name, AtomosLayer layer,
        AtomosLayer expectedLayer, boolean expectedToFind)
    {
        final Optional<AtomosContent> result = layer.findAtomosContent(name);
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

    private void checkBundleStates(Bundle[] bundles)
    {
        assertTrue(bundles.length > 0, "No bundles: " + Arrays.toString(bundles));
        for (final Bundle b : bundles)
        {
            final String msg = b.getBundleId() + " " + b.getLocation() + ": "
                + b.getSymbolicName() + ": " + getState(b);
            System.out.println(msg);
            int expected;
            if ("osgi.annotation".equals(b.getSymbolicName())
                || "org.osgi.service.component.annotations".equals(b.getSymbolicName()))
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
                catch (final Throwable t)
                {
                    t.printStackTrace();
                }
            }
            assertEquals(expected, b.getState(), "Wrong bundle state for bundle: " + msg);
        }
    }

    private void checkClassBundle(Object service, ServiceReference<?> ref)
    {
        final Class<?> serviceClass = service.getClass();
        final Bundle b = FrameworkUtil.getBundle(serviceClass);
        assertEquals(ref.getBundle(), b, "Wrong bundle.");
        assertEquals(b.getSymbolicName(), service.getClass().getModule().getName(),
            "Wrong module name");
    }

    private void checkLayer(AtomosLayer atomosLayer, LoaderType loaderType, int id)
    {
        assertEquals(id, atomosLayer.getId(), "Wrong id.");
        assertEquals(loaderType, atomosLayer.getLoaderType(), "Wrong loaderType");
        assertEquals(loaderType.toString(), atomosLayer.getName(), "Wrong name.");
    }

    private void checkLoader(AtomosRuntime runtime, AtomosLayer layer,
        LoaderType loaderType) throws ClassNotFoundException
    {
        final Set<AtomosContent> atomosBundles = layer.getAtomosContents();
        final List<Class<?>> classes = new ArrayList<>();
        for (final AtomosContent atomosBundle : atomosBundles)
        {
            final Bundle b = atomosBundle.getBundle();
            assertNotNull(b, "No bundle found: " + atomosBundle.getSymbolicName());
            final String name = getTestClassName(b);
            assertNotNull(name, "No class name.");
            classes.add(b.loadClass(name));
        }
        final Set<ClassLoader> classLoaders = new HashSet<>();
        for (final Class<?> clazz : classes)
        {
            classLoaders.add(clazz.getClassLoader());
        }
        switch (loaderType)
        {
            case OSGI:
                for (final ClassLoader classLoader : classLoaders)
                {
                    assertTrue(classLoader instanceof BundleReference,
                        "Class loader is not a BundleReference");
                }
                assertEquals(5, classLoaders.size(), "Wrong number of class loaders.");
                break;
            case MANY:
                for (final ClassLoader classLoader : classLoaders)
                {
                    assertFalse(classLoader instanceof BundleReference,
                        "Class loader is a BundleReference");
                }
                assertEquals(5, classLoaders.size(), "Wrong number of class loaders.");
                break;
            case SINGLE:
                assertEquals(1, classLoaders.size(), "Wrong number of class loaders.");
                break;

            default:
                fail();
        }
    }

    private void checkServices(BundleContext bc, int expectedNumber)
        throws InvalidSyntaxException
    {
        final ServiceReference<?>[] echoRefs = bc.getAllServiceReferences(
            Echo.class.getName(), null);
        assertNotNull(echoRefs, "No Echo service ref found.");
        assertEquals(expectedNumber, echoRefs.length, "Wrong number of services.");
        for (final ServiceReference<?> ref : echoRefs)
        {
            final Echo echo = (Echo) bc.getService(ref);
            assertNotNull(echo, "No Echo service found.");
            assertEquals(ref.getProperty("type") + " Hello!!", echo.echo("Hello!!"),
                "Wrong Echo.");
            checkClassBundle(echo, ref);
        }
    }

    private Framework getFramework(String... args) throws BundleException
    {
        return AtomosLauncher.launch(AtomosLauncher.getConfiguration(args));
    }
    private ClassLoader getCLForResourceTests(Path storage) throws BundleException
    {
        testFramework = getFramework(
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(),
                AtomosLauncher.ATOMOS_MODULES_DIR
                + "=target/modules/" + TESTBUNDLES_RESOURCE_A
                + "-" + ATOMOS_VERSION + ".jar");

        final BundleContext bc = testFramework.getBundleContext();
        checkBundleStates(bc.getBundles());

        final ServiceReference<AtomosRuntime> atomosRef = bc.getServiceReference(
            AtomosRuntime.class);
        final AtomosRuntime atomos = bc.getService(atomosRef);
        final AtomosLayer bootLayer = atomos.getBootLayer();
        final Set<AtomosLayer> children = bootLayer.getChildren();
        assertNotNull(children, "Null children.");
        assertEquals(1, children.size(), "Wrong number of children.");

        final AtomosLayer child = children.iterator().next();
        assertEquals(1, child.getAtomosContents().size(), "Wrong number of bundles.");
        Module serviceLibModule = null;
        for (final AtomosContent atomosBundle : child.getAtomosContents())
        {
            if (atomosBundle.getSymbolicName().equals(
                TESTBUNDLES_RESOURCE_A))
            {
                serviceLibModule = atomosBundle.adapt(Module.class).get();
            }
        }
        return serviceLibModule.getClassLoader();
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

    private String getTestClassName(Bundle b)
    {
        switch (b.getSymbolicName())
        {
            case TESTBUNDLES_SERVICE_IMPL_A:
                return TESTBUNDLES_SERVICE_IMPL_A + ".EchoImpl";
            case TESTBUNDLES_SERVICE_IMPL_B:
                return TESTBUNDLES_SERVICE_IMPL_B + ".EchoImpl";
            case TESTBUNDLES_SERVICE_LIBRARY:
                return TESTBUNDLES_SERVICE_LIBRARY + ".SomeUtil";
            case TESTBUNDLES_SERVICE_USER:
                return TESTBUNDLES_SERVICE_USER + ".EchoUser";
            case TESTBUNDLES_RESOURCE_A:
                return RESSOURCE_A_CLAZZ_NAME;
            default:
                fail("Unknown");
        }
        return null;
    }

    private AtomosLayer installChild(AtomosLayer parent, String name,
        AtomosRuntime atomosRuntime, LoaderType loaderType) throws BundleException
    {
        final File modules = new File("target/modules");
        assertTrue(modules.isDirectory(), "Modules directory does not exist: " + modules);

        final AtomosLayer child = parent.addLayer(name, loaderType, modules.toPath());

        final List<Bundle> bundles = new ArrayList<>();
        for (final AtomosContent atomosBundle : child.getAtomosContents())
        {
            bundles.add(atomosBundle.install("child"));
        }
        for (final Bundle b : bundles)
        {
            b.start();
        }

        return child;
    }

    @Test
    void testAddNewLayers(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException, InterruptedException
    {
        testFramework = getFramework(
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(),
            AtomosLauncher.ATOMOS_MODULES_DIR + "=target/modules");
        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        final Bundle[] bundles = bc.getBundles();
        final int originalNum = bundles.length;
        assertTrue(bundles.length > 0, "No bundles: " + Arrays.toString(bundles));

        checkServices(bc, 4);

        final AtomosRuntime atomosRuntime = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));

        final Collection<AtomosLayer> layers = new ArrayList<>();
        for (int i = 0; i < 20; i++)
        {
            layers.add(installChild(atomosRuntime.getBootLayer(), "services-" + i,
                atomosRuntime, LoaderType.OSGI));
        }
        checkServices(bc, 44);

        checkBundleStates(bc.getBundles());

        final List<Bundle> allChildBundles = layers.stream().flatMap(
            (l) -> l.getAtomosContents().stream()).map(
                AtomosContent::getBundle).filter(
                    Objects::nonNull).collect(Collectors.toList());

        final AtomosLayer firstChild = layers.iterator().next();
        final Set<AtomosContent> firstChildContents = firstChild.getAtomosContents();

        List<Bundle> firstChildBundles = firstChildContents.stream().map(
            AtomosContent::getBundle).filter(
                Objects::nonNull).collect(Collectors.toList());

        assertEquals(5, firstChildBundles.size(),
            "Wrong number of bundles in first child.");
        firstChildBundles.forEach((b) -> {
            try
            {
                b.uninstall();
            }
            catch (final BundleException e)
            {
                throw new RuntimeException(e);
            }
        });

        // note that doing a bundle uninstall forces the location to be disconnected
        firstChildBundles.forEach((b) -> {
            assertNull(atomosRuntime.getConnectedContent(b.getLocation()),
                "Atomos content not expected.");
        });

        firstChildBundles = firstChildContents.stream().map(
            AtomosContent::getBundle).filter(
                Objects::nonNull).collect(Collectors.toList());
        assertEquals(0, firstChildBundles.size(),
            "Wrong number of bundles in first child.");

        layers.forEach((l) -> {
            try
            {
                l.uninstall();
            }
            catch (final BundleException e)
            {
                throw new RuntimeException(e);
            }
        });
        checkServices(bc, 4);

        // uninstalling the layer forces all of its content to get disconnected
        allChildBundles.forEach((b) -> assertNull(atomosRuntime.getConnectedContent(b.getLocation()),
            "Atomos content not expected."));

        assertEquals(originalNum, bc.getBundles().length,
            "Wrong number of final bundles.");
    }

    @Test
    void testFindBundle(@TempDir Path storage) throws BundleException
    {
        testFramework = getFramework(
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());
        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final AtomosRuntime atomosRuntime = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));
        final AtomosLayer child = installChild(atomosRuntime.getBootLayer(), "SINGLE",
            atomosRuntime, LoaderType.SINGLE);
        assertFindBundle("java.base", child, atomosRuntime.getBootLayer(), true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL_A, child, child, true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL, child,
            atomosRuntime.getBootLayer(), true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL_A,
            atomosRuntime.getBootLayer(), null, false);
    }

    @Test
    void testGetEntry(@TempDir Path storage) throws BundleException
    {
        testFramework = getFramework(
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final AtomosRuntime atomosRuntime = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));
        final AtomosLayer child = installChild(atomosRuntime.getBootLayer(), "SINGLE",
            atomosRuntime, LoaderType.SINGLE);
        final Bundle b = child.findAtomosContent(
            TESTBUNDLES_SERVICE_IMPL_A).get().getBundle();
        assertNotNull(b, "No bundle found.");
        URL mf = b.getEntry("/META-INF/MANIFEST.MF");
        assertNotNull(mf, "No manifest found.");
        mf = b.getEntry("META-INF/MANIFEST.MF");
        assertNotNull(mf, "No manifest found.");
    }

    @Test
    void testInstallDifferentPrefix(@TempDir Path storage) throws BundleException
    {
        testFramework = getFramework(
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final AtomosRuntime atomosRuntime = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));
        final AtomosLayer child = installChild(atomosRuntime.getBootLayer(), "SINGLE",
            atomosRuntime, LoaderType.SINGLE);
        final AtomosContent ab = child.findAtomosContent(
            TESTBUNDLES_SERVICE_IMPL_A).get();
        Bundle b = ab.getBundle();
        assertNotNull(b, "No bundle found.");

        try
        {
            ab.install("shouldFail");
            fail("Should not be able to install with different prefix");
        }
        catch (final BundleException e)
        {
            // expected
        }

        final Bundle existing = ab.install("child");
        assertNotNull(existing, "No bundle.");
        assertEquals(b, existing, "Existing bundle doesn't equal original.");

        // now try to uninstall and use a different prefix
        existing.uninstall();
        b = ab.install("testPrefix");
        assertTrue(b.getLocation().startsWith("testPrefix:"),
            "Wrong location prefix: " + b.getLocation());
        b.start();
    }

    @Test
    void testInvalidUseOfRuntime(
        @TempDir Path storage)
            throws BundleException, InterruptedException
    {
        final File storage1 = new File(storage.toFile(), "s1");
        storage1.mkdirs();
        final File storage2 = new File(storage.toFile(), "s2");
        storage2.mkdirs();

        testFramework = getFramework(
                Constants.FRAMEWORK_STORAGE + '='
                + storage1.getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final AtomosRuntime atomosRuntime = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));

        Framework f = null;
        try
        {
            f = AtomosLauncher.newFramework(
                Map.of(Constants.FRAMEWORK_STORAGE, storage2.getAbsolutePath(),
                    "felix.cache.locking", "false"),
                atomosRuntime);
            f.start();
            fail();
        }
        catch (final IllegalStateException e)
        {
            // expected
        }
        finally
        {
            if (f != null)
            {
                f.stop();
                f.waitForStop(5000);
            }
        }
    }

    @Test
    void testLoaderType(@TempDir Path storage) throws BundleException,
    ClassNotFoundException
    {
        testFramework = getFramework(
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final AtomosRuntime atomosRuntime = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));
        checkLoader(atomosRuntime, installChild(atomosRuntime.getBootLayer(), "OSGI",
            atomosRuntime, LoaderType.OSGI), LoaderType.OSGI);
        checkLoader(atomosRuntime, installChild(atomosRuntime.getBootLayer(), "SINGLE",
            atomosRuntime, LoaderType.SINGLE), LoaderType.SINGLE);
        checkLoader(atomosRuntime, installChild(atomosRuntime.getBootLayer(), "MANY",
            atomosRuntime, LoaderType.MANY), LoaderType.MANY);
    }

    @Test
    void testLoadFromModule(@TempDir Path storage)
        throws BundleException
    {
        testFramework = getFramework(
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(),
            AtomosLauncher.ATOMOS_MODULES_DIR + "=target/modules");

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        checkBundleStates(bc.getBundles());

        final ServiceReference<AtomosRuntime> atomosRef = bc.getServiceReference(
            AtomosRuntime.class);
        assertNotNull(atomosRef, "No Atomos runtime.");

        final AtomosRuntime atomos = bc.getService(atomosRef);
        assertNotNull(atomos, "Null Atomos runtime.");
        final AtomosLayer bootLayer = atomos.getBootLayer();
        assertNotNull(bootLayer, "The boot layer is null.");
        final Set<AtomosLayer> children = bootLayer.getChildren();
        assertNotNull(children, "Null children.");
        assertEquals(1, children.size(), "Wrong number of children.");

        final AtomosLayer child = children.iterator().next();
        assertEquals(5, child.getAtomosContents().size(), "Wrong number of bundles.");
        Module serviceLibModule = null;
        for (final AtomosContent atomosBundle : child.getAtomosContents())
        {
            if (atomosBundle.getSymbolicName().equals(
                TESTBUNDLES_SERVICE_LIBRARY))
            {
                serviceLibModule = atomosBundle.adapt(Module.class).get();
            }
        }
        try
        {
            final Class<?> clazz = serviceLibModule.getClassLoader().loadClass(
                TESTBUNDLES_SERVICE_LIBRARY + ".SomeUtil");
            assertNotNull(clazz, "Null class from loadClass.");
        }
        catch (final Exception e)
        {
            fail("Failed to find class: " + e.getMessage());
        }
    }

    @Test
    void testModuleDirServices(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException
    {
        testFramework = getFramework(
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(),
            AtomosLauncher.ATOMOS_MODULES_DIR + "=target/modules");

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        checkBundleStates(bc.getBundles());

        checkServices(bc, 4);
    }

    @Test
    void testModulePathServices(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException
    {
        testFramework = getFramework(
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        checkBundleStates(bc.getBundles());

        checkServices(bc, 2);
    }

    @Test
    void testPersistLayers(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException, InterruptedException
    {
        testFramework = getFramework(
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final AtomosRuntime atomosRuntime1 = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));
        installChild(atomosRuntime1.getBootLayer(), "SINGLE", atomosRuntime1,
            LoaderType.SINGLE);
        installChild(atomosRuntime1.getBootLayer(), "MANY", atomosRuntime1,
            LoaderType.MANY);
        installChild(atomosRuntime1.getBootLayer(), "OSGI", atomosRuntime1,
            LoaderType.OSGI);

        checkBundleStates(bc.getBundles());
        checkServices(bc, 8);

        testFramework.stop();
        testFramework.waitForStop(10000);

        // try starting the framework directly again
        testFramework.start();
        bc = testFramework.getBundleContext();

        final AtomosLayer child1 = installChild(atomosRuntime1.getBootLayer(), "SINGLE2",
            atomosRuntime1, LoaderType.SINGLE);

        checkServices(bc, 10);

        testFramework.stop();
        testFramework.waitForStop(10000);

        testFramework.start();
        bc = testFramework.getBundleContext();
        checkServices(bc, 10);

        child1.uninstall();
        checkServices(bc, 8);

        testFramework.stop();
        testFramework.waitForStop(10000);

        // test persistent load with a new Runtime
        testFramework = getFramework(
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        checkServices(bc, 8);

        final AtomosRuntime atomosRuntime2 = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));
        final AtomosLayer bootLayer = atomosRuntime2.getBootLayer();
        assertEquals(3, bootLayer.getChildren().size(), "Wrong number of children.");

        final List<AtomosLayer> children = bootLayer.getChildren().stream().sorted(
            Comparator.comparingLong(AtomosLayer::getId)).collect(
                Collectors.toList());
        checkLayer(children.get(0), LoaderType.SINGLE, 2);
        checkLayer(children.get(1), LoaderType.MANY, 3);
        checkLayer(children.get(2), LoaderType.OSGI, 4);

        // uninstall service.impl.a bundle from the first child
        children.iterator().next().getAtomosContents().stream().map(
            AtomosContent::getBundle).filter(
                Objects::nonNull).filter(
                    (b) -> b.getSymbolicName().equals(
                        TESTBUNDLES_SERVICE_IMPL_A)).findFirst().orElseThrow().uninstall();
        checkServices(bc, 7);

        testFramework.stop();
        testFramework.waitForStop(10000);

        // startup with the option not to force install all atomos contents
        testFramework = getFramework(
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(),
            AtomosRuntime.ATOMOS_CONTENT_INSTALL + "=false");

        bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        checkServices(bc, 7);
    }

    @Test
    void testReferenceUser(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException
    {
        testFramework = getFramework(
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final AtomosRuntime atomosRuntime = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));
        final AtomosLayer child = installChild(atomosRuntime.getBootLayer(), "testRef",
            atomosRuntime, LoaderType.MANY);
        checkServices(bc, 4);
        final AtomosContent ab = child.findAtomosContent(
            TESTBUNDLES_SERVICE_USER).get();
        final Bundle b = ab.getBundle();
        assertNotNull(b, "No bundle found.");

        final ServiceReference<?>[] refs = b.getRegisteredServices();
        assertNotNull(refs, "No services.");
        assertEquals(2, refs.length, "Wrong number of services.");
        assertEquals(Boolean.TRUE, refs[0].getProperty("echo.reference"),
            "Wrong service.");
        assertEquals(Boolean.TRUE, refs[1].getProperty("echo.reference"),
            "Wrong service.");
    }

    @Test
    void testResourceGetMissingResource(@TempDir Path storage)
        throws BundleException
    {
        try
        {
            final Class<?> clazz = getCLForResourceTests(storage).loadClass(
                RESSOURCE_A_CLAZZ_NAME);
            final URL u = clazz.getResource("/META-TEXT/noFile.txt");
            assertNull(u, "get of non-existent resource should return null.");
        }
        catch (final ClassNotFoundException e)
        {
            fail("Failed to find class: " + e.getMessage());
        }
    }

    @Test
    void testResourceLoadResource(@TempDir Path storage)
        throws BundleException
    {
        try
        {
            final Class<?> clazz = getCLForResourceTests(storage).loadClass(
                RESSOURCE_A_CLAZZ_NAME);
            final URL resc = clazz.getResource("/META-TEXT/file.txt");
            assertNotNull(resc, "Expected URL, got null ");
            assertNotNull(resc.getFile(), "Could not get resource from URL");
        }
        catch (final ClassNotFoundException e)
        {
            fail("Failed to find class: " + e.getMessage());
        }
    }

    @Test
    void testResourcePackagedResource(@TempDir Path storage)
        throws BundleException, IOException
    {
        try
        {
            final Class<?> clazz = getCLForResourceTests(storage).loadClass(
                RESSOURCE_A_CLAZZ_NAME);
            final URL resc = clazz.getResource("file.txt");
            assertNotNull(resc, "Expected URL, got null ");
            assertEquals("/org/atomos/tests/testbundles/resource/a/file.txt",
                new BufferedReader(new InputStreamReader(resc.openStream())).readLine(),
                "Incorrect contents from URL");
        }
        catch (final ClassNotFoundException e)
        {
            fail("Failed to find class: " + e.getMessage());
        }
    }

    @Test
    void testResourceRootResource(@TempDir Path storage)
        throws BundleException, IOException
    {
        try
        {
            final Class<?> clazz = getCLForResourceTests(storage).loadClass(
                RESSOURCE_A_CLAZZ_NAME);
            final URL resc = clazz.getResource("/file.txt");
            assertNotNull(resc, "Expected URL, got null ");
            assertEquals("/file.txt", new BufferedReader(
                new InputStreamReader(resc.openStream())).readLine(),
                "Incorrect contents from URL");
        }
        catch (final ClassNotFoundException e)
        {
            fail("Failed to find class: " + e.getMessage());
        }
    }

    private static String BSN_CONTRACT = "org.apache.felix.atomos.tests.testbundles.service.contract";
    private static String BSN_SERVICE_IMPL = "org.apache.felix.atomos.tests.testbundles.service.impl";
    @Test
    void testConnectLocation(@TempDir Path storage)
        throws BundleException, InterruptedException
    {
        String[] args = new String[] {
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(),
                AtomosRuntime.ATOMOS_CONTENT_INSTALL + "=false" };

        testFramework = getFramework(args);
        BundleContext bc = testFramework.getBundleContext();

        assertNotNull(bc, "No context found.");
        assertEquals(1, bc.getBundles().length, "Wrong number of bundles.");

        AtomosRuntime runtime = bc.getService(
            bc.getServiceReference(AtomosRuntime.class));

        AtomosContent systemContent = runtime.getConnectedContent(
            Constants.SYSTEM_BUNDLE_LOCATION);
        assertNotNull(systemContent, "Did not find system content");
        try
        {
            systemContent.connect("should fail");
            fail("Expected failure.");
        }
        catch (IllegalStateException e)
        {
            // expected
        }

        String switchLocation = "switch.location";
        AtomosContent contractContent = runtime.getBootLayer().findAtomosContent(BSN_CONTRACT).get();
        AtomosContent implContent = runtime.getBootLayer().findAtomosContent(BSN_SERVICE_IMPL).get();
        assertNotNull(contractContent, "no contract content found.");
        assertNotNull(implContent, "no impl content found.");

        contractContent.connect(switchLocation);
        assertEquals(switchLocation,
            contractContent.getConnectLocation(),
            "Wrong connect location.");

        // stop and start same framework instance after connecting to make sure the connection stays
        testFramework.stop();
        testFramework.waitForStop(5000);
        testFramework.start();
        bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        assertEquals(1, bc.getBundles().length, "Wrong number of bundles.");

        // install the location connected.
        Bundle switchBundle = bc.installBundle(switchLocation);
        assertEquals(contractContent.getSymbolicName(),
            switchBundle.getSymbolicName(),
            "Wrong BSN");
        assertEquals(switchBundle, contractContent.getBundle(), "Wrong bundle.");
        assertEquals(contractContent, runtime.getConnectedContent(switchLocation));

        // disconnect while bundle is installed
        contractContent.disconnect();
        assertNull(runtime.getConnectedContent(switchLocation),
            "Expected no connected content.");
        assertNull(contractContent.getBundle(), "Expected no bundle.");

        try
        {
            // should fail update if not connected
            switchBundle.update();
            fail("Expected failure to update: " + switchBundle);
        }
        catch (BundleException e)
        {
            // expected
        }

        // bundle should still be the same as before failed update attempt
        assertEquals(contractContent.getSymbolicName(),
            switchBundle.getSymbolicName(),
            "Wrong BSN");

        // connect the location to a different content
        implContent.connect(switchLocation);
        // still should not give a bundle because the bundle is really using the other content
        assertNull(runtime.getConnectedContent(switchLocation),
            "Expected no connected content.");
        assertNull(implContent.getBundle(), "Expected no bundle.");

        switchBundle.update();
        assertEquals(implContent.getSymbolicName(),
            switchBundle.getSymbolicName(),
            "Wrong BSN");
        assertEquals(switchBundle, implContent.getBundle(), "Wrong bundle.");
        assertEquals(implContent, runtime.getConnectedContent(switchLocation));


        testFramework.stop();
        testFramework.waitForStop(5000);

        // start over from persistence
        runtime = AtomosRuntime.newAtomosRuntime();
        implContent = runtime.getBootLayer().findAtomosContent(BSN_SERVICE_IMPL).get();

        testFramework = AtomosLauncher.newFramework(AtomosLauncher.getConfiguration(args),
            runtime);
        testFramework.start();
        bc = testFramework.getBundleContext();

        assertEquals(2, bc.getBundles().length, "Wrong number of bundles.");
        switchBundle = bc.getBundle(switchLocation);
        assertNotNull(switchBundle, "Found no bundle: " + switchLocation);
        assertEquals(implContent.getSymbolicName(), switchBundle.getSymbolicName(),
            "Wrong BSN");



        assertEquals(switchBundle, implContent.getBundle(), "Wrong bundle.");

        switchBundle.uninstall();
        assertEquals(switchLocation,
            implContent.getConnectLocation(),
            "Wrong connect location");
        assertNull(runtime.getConnectedContent(implContent.getConnectLocation()),
            "Unexpected connected content.");

        implContent.disconnect();
        assertNull(runtime.getConnectedContent(switchLocation),
            "Expected no connected content.");
        assertNull(implContent.getBundle(), "Expected no bundle.");
        try
        {
            Bundle fail = bc.installBundle(switchLocation);
            fail("Expected failure to install: " + fail);
        }
        catch (BundleException e)
        {
            // expected
        }

        testFramework.stop();
        testFramework.waitForStop(5000);

    }
}
