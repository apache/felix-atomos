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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.felix.atomos.Atomos;
import org.apache.felix.atomos.Atomos.HeaderProvider;
import org.apache.felix.atomos.AtomosContent;
import org.apache.felix.atomos.AtomosLayer;
import org.apache.felix.atomos.AtomosLayer.LoaderType;
import org.apache.felix.atomos.tests.testbundles.service.contract.Echo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.wiring.BundleWiring;

public class ModulepathLaunchTest
{

    private static final String TESTBUNDLES_SERVICE_IMPL = "org.apache.felix.atomos.tests.testbundles.service.impl";
    private static final String TESTBUNDLES_SERVICE_IMPL_A = "org.apache.felix.atomos.tests.testbundles.service.impl.a";
    private static final String TESTBUNDLES_SERVICE_IMPL_B = "org.apache.felix.atomos.tests.testbundles.service.impl.b";
    private static final String TESTBUNDLES_SERVICE_LIBRARY = "org.apache.felix.atomos.tests.testbundles.service.library";
    private static final String TESTBUNDLES_SERVICE_USER = "org.apache.felix.atomos.tests.testbundles.service.user";
    private static final String TESTBUNDLES_RESOURCE_A = "org.apache.felix.atomos.tests.testbundles.resource.a";
    private static final String TESTBUNDLES_DEPENDENCY_A = "org.apache.felix.atomos.tests.testbundles.dependency.a";
    private static final String TESTBUNDLES_DEPENDENCY_B = "org.apache.felix.atomos.tests.testbundles.dependency.b";
    private static final String TESTBUNDLES_DEPENDENT_X = "org.apache.felix.atomos.tests.testbundles.dependent.x";
    private static final int NUM_MODULES_DIR = 8;

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

    private void checkBundleStates(Atomos atomos, Bundle[] bundles)
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
                    // Java 11 has a bug with automatic modules
                    // this causes org.osgi.framework to not be available
                    // when using SINGLE or MULTIPLE built-in loaders
                    if (b.getState() == Bundle.RESOLVED)
                    {
                        AtomosContent content = atomos.getConnectedContent(
                            b.getLocation());
                        AtomosLayer layer = content.getAtomosLayer();
                        if (Runtime.version().feature() == 11 //
                            && layer.getLoaderType() != LoaderType.OSGI //
                            && b.getSymbolicName().equals(TESTBUNDLES_DEPENDENT_X))
                        {
                            expected = Bundle.RESOLVED;
                        }
                    }
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

    private void checkLoader(Atomos runtime, AtomosLayer layer,
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
                assertEquals(NUM_MODULES_DIR, classLoaders.size(),
                    "Wrong number of class loaders.");
                break;
            case MANY:
                for (final ClassLoader classLoader : classLoaders)
                {
                    assertFalse(classLoader instanceof BundleReference,
                        "Class loader is a BundleReference");
                }
                assertEquals(NUM_MODULES_DIR, classLoaders.size(),
                    "Wrong number of class loaders.");
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

    private Framework getFramework(Path modules, String... args) throws BundleException
    {
        Map<String, String> config = Atomos.getConfiguration(args);
        Atomos atomos = Atomos.newAtomos(config);
        if (modules != null)
        {
            atomos.getBootLayer().addModules("modules", modules);
        }
        Framework framework = atomos.newFramework(config);
        framework.start();
        return framework;
    }

    private Framework getFramework(Path modules, HeaderProvider provider, String... args)
        throws BundleException
    {
        Map<String, String> config = Atomos.getConfiguration(args);
        Atomos atomos = Atomos.newAtomos(config, provider);
        if (modules != null)
        {
            atomos.getBootLayer().addModules("modules", modules);
        }
        Framework framework = atomos.newFramework(config);
        framework.start();
        return framework;
    }

    private ClassLoader getCLForResourceTests(Path storage) throws BundleException
    {
        Path[] testBundle = findModulePaths(TESTBUNDLES_RESOURCE_A);
        testFramework = getFramework(testBundle[0],
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        final ServiceReference<Atomos> atomosRef = bc.getServiceReference(
            Atomos.class);
        final Atomos atomos = bc.getService(atomosRef);
        checkBundleStates(atomos, bc.getBundles());

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
            case TESTBUNDLES_DEPENDENCY_A:
                return TESTBUNDLES_DEPENDENCY_A + ".A";
            case TESTBUNDLES_DEPENDENCY_B:
                return TESTBUNDLES_DEPENDENCY_B + ".B";
            case TESTBUNDLES_DEPENDENT_X:
                return TESTBUNDLES_DEPENDENT_X + ".X";
            default:
                fail("Unknown");
        }
        return null;
    }

    private AtomosLayer installChild(AtomosLayer parent, String name,
        Atomos atomos, LoaderType loaderType) throws BundleException
    {
        final File modules = new File("target/modules");
        assertTrue(modules.isDirectory(), "Modules directory does not exist: " + modules);

        final AtomosLayer child = parent.addLayer(name, loaderType, modules.toPath());

        startBundles(child);

        return child;
    }

    @Test
    void testAddNewLayers(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException, InterruptedException
    {
        Path modules = new File("target/modules").toPath();
        testFramework = getFramework(modules,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());
        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        final Bundle[] bundles = bc.getBundles();
        final int originalNum = bundles.length;
        assertTrue(bundles.length > 0, "No bundles: " + Arrays.toString(bundles));

        checkServices(bc, 4);

        final Atomos atomos = bc.getService(
            bc.getServiceReference(Atomos.class));

        final Collection<AtomosLayer> layers = new ArrayList<>();
        for (int i = 0; i < 20; i++)
        {
            layers.add(installChild(atomos.getBootLayer(), "services-" + i,
                atomos, LoaderType.OSGI));
        }
        checkServices(bc, 44);

        checkBundleStates(atomos, bc.getBundles());

        final List<Bundle> allChildBundles = layers.stream().flatMap(
            (l) -> l.getAtomosContents().stream()).map(
                AtomosContent::getBundle).filter(
                    Objects::nonNull).collect(Collectors.toList());

        final AtomosLayer firstChild = layers.iterator().next();
        final Set<AtomosContent> firstChildContents = firstChild.getAtomosContents();

        List<Bundle> firstChildBundles = firstChildContents.stream().map(
            AtomosContent::getBundle).filter(
                Objects::nonNull).collect(Collectors.toList());

        assertEquals(NUM_MODULES_DIR, firstChildBundles.size(),
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
            assertNull(atomos.getConnectedContent(b.getLocation()),
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
        allChildBundles.forEach((b) -> assertNull(atomos.getConnectedContent(b.getLocation()),
            "Atomos content not expected."));

        assertEquals(originalNum, bc.getBundles().length,
            "Wrong number of final bundles.");
    }

    @Test
    void testAddLayerBeforeNewFrameworkAtomos(@TempDir Path storage)
        throws BundleException
    {
        doAddLayerBeforeFramework(storage, true);
    }

    @Test
    void testAddLayerBeforeFrameworkFactory(@TempDir Path storage) throws BundleException
    {
        doAddLayerBeforeFramework(storage, false);
    }

    void doAddLayerBeforeFramework(Path storage, boolean useAtomosNewFramework)
        throws BundleException
    {
        Atomos atomos = Atomos.newAtomos();
        final File modules = new File("target/modules");
        assertTrue(modules.isDirectory(), "Modules directory does not exist: " + modules);
        final AtomosLayer child = atomos.getBootLayer().addLayer("SINGLE",
            LoaderType.SINGLE, modules.toPath());

        if (useAtomosNewFramework)
        {
            testFramework = atomos.newFramework(
                Map.of(Constants.FRAMEWORK_STORAGE, storage.toFile().getAbsolutePath()));
        }
        else
        {
            testFramework = ServiceLoader.load(
                ConnectFrameworkFactory.class).findFirst().map(
                    f -> f.newFramework( //
                        Map.of(//
                            Constants.FRAMEWORK_STORAGE, //
                            storage.toFile().getAbsolutePath(), //
                            Constants.FRAMEWORK_SYSTEMPACKAGES, //
                            ""),
                        atomos.getModuleConnector())).get();
        }
        testFramework.start();

        assertFindBundle("java.base", child, atomos.getBootLayer(), true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL_A, child, child, true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL, child, atomos.getBootLayer(), true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL_A, atomos.getBootLayer(), null, false);
    }

    @Test
    void testFindBundle(@TempDir Path storage) throws BundleException
    {
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());
        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final Atomos atomos = bc.getService(
            bc.getServiceReference(Atomos.class));
        final AtomosLayer child = installChild(atomos.getBootLayer(), "SINGLE",
            atomos, LoaderType.SINGLE);
        assertFindBundle("java.base", child, atomos.getBootLayer(), true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL_A, child, child, true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL, child,
            atomos.getBootLayer(), true);
        assertFindBundle(TESTBUNDLES_SERVICE_IMPL_A,
            atomos.getBootLayer(), null, false);
    }

    @Test
    void testGetEntry(@TempDir Path storage) throws BundleException
    {
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final Atomos atomos = bc.getService(
            bc.getServiceReference(Atomos.class));
        final AtomosLayer child = installChild(atomos.getBootLayer(), "SINGLE",
            atomos, LoaderType.SINGLE);
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
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final Atomos atomos = bc.getService(
            bc.getServiceReference(Atomos.class));
        final AtomosLayer child = installChild(atomos.getBootLayer(), "SINGLE",
            atomos, LoaderType.SINGLE);
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

        testFramework = getFramework(null,
                Constants.FRAMEWORK_STORAGE + '='
                + storage1.getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final Atomos atomos = bc.getService(
            bc.getServiceReference(Atomos.class));

        Framework f = null;
        try
        {
            f = atomos.newFramework(
                Map.of(Constants.FRAMEWORK_STORAGE, storage2.getAbsolutePath()));
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
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final Atomos atomos = bc.getService(
            bc.getServiceReference(Atomos.class));
        checkLoader(atomos, installChild(atomos.getBootLayer(), "SINGLE",
            atomos, LoaderType.SINGLE), LoaderType.SINGLE);
        checkLoader(atomos, installChild(atomos.getBootLayer(), "MANY",
            atomos, LoaderType.MANY), LoaderType.MANY);
        checkLoader(atomos, installChild(atomos.getBootLayer(), "OSGI",
            atomos, LoaderType.OSGI), LoaderType.OSGI);
    }

    @Test
    void testLoadFromModule(@TempDir Path storage)
        throws BundleException
    {
        Path modules = new File("target/modules").toPath();
        testFramework = getFramework(modules,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        final ServiceReference<Atomos> atomosRef = bc.getServiceReference(
            Atomos.class);
        assertNotNull(atomosRef, "No Atomos runtime.");

        final Atomos atomos = bc.getService(atomosRef);
        checkBundleStates(atomos, bc.getBundles());

        assertNotNull(atomos, "Null Atomos runtime.");
        final AtomosLayer bootLayer = atomos.getBootLayer();
        assertNotNull(bootLayer, "The boot layer is null.");
        final Set<AtomosLayer> children = bootLayer.getChildren();
        assertNotNull(children, "Null children.");
        assertEquals(1, children.size(), "Wrong number of children.");

        final AtomosLayer child = children.iterator().next();
        assertEquals(NUM_MODULES_DIR, child.getAtomosContents().size(),
            "Wrong number of bundles.");
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
        Path modules = new File("target/modules").toPath();
        testFramework = getFramework(modules,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        final ServiceReference<Atomos> atomosRef = bc.getServiceReference(
            Atomos.class);
        assertNotNull(atomosRef, "No Atomos runtime.");

        final Atomos atomos = bc.getService(atomosRef);
        checkBundleStates(atomos, bc.getBundles());

        checkServices(bc, 4);
    }

    @Test
    void testModulePathServices(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException
    {
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");
        final ServiceReference<Atomos> atomosRef = bc.getServiceReference(
            Atomos.class);
        assertNotNull(atomosRef, "No Atomos runtime.");

        final Atomos atomos = bc.getService(atomosRef);
        checkBundleStates(atomos, bc.getBundles());

        checkServices(bc, 2);
    }

    @Test
    void testPersistLayers(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException, InterruptedException
    {
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final Atomos atomos1 = bc.getService(
            bc.getServiceReference(Atomos.class));
        installChild(atomos1.getBootLayer(), "SINGLE", atomos1,
            LoaderType.SINGLE);
        installChild(atomos1.getBootLayer(), "MANY", atomos1,
            LoaderType.MANY);
        installChild(atomos1.getBootLayer(), "OSGI", atomos1,
            LoaderType.OSGI);

        checkBundleStates(atomos1, bc.getBundles());
        checkServices(bc, 8);

        testFramework.stop();
        testFramework.waitForStop(10000);

        // try starting the framework directly again
        testFramework.start();
        bc = testFramework.getBundleContext();

        final AtomosLayer child1 = installChild(atomos1.getBootLayer(), "SINGLE2",
            atomos1, LoaderType.SINGLE);

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
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        checkServices(bc, 8);

        final Atomos atomos2 = bc.getService(
            bc.getServiceReference(Atomos.class));
        final AtomosLayer bootLayer = atomos2.getBootLayer();
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
        testFramework = getFramework(null,
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(),
            Atomos.ATOMOS_CONTENT_INSTALL + "=false");

        bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        checkServices(bc, 7);
    }

    @Test
    void testReferenceUser(@TempDir Path storage)
        throws BundleException, InvalidSyntaxException
    {
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final Atomos atomos = bc.getService(
            bc.getServiceReference(Atomos.class));
        final AtomosLayer child = installChild(atomos.getBootLayer(), "testRef",
            atomos, LoaderType.MANY);
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

    private static final String BSN_CONTRACT = "atomos.service.contract";
    private static final String BSN_SERVICE_IMPL = "org.apache.felix.atomos.tests.testbundles.service.impl";
    @Test
    void testConnectLocation(@TempDir Path storage)
        throws BundleException, InterruptedException
    {
        String[] args = new String[] {
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(),
                Atomos.ATOMOS_CONTENT_INSTALL + "=false" };

        testFramework = getFramework(null, args);
        BundleContext bc = testFramework.getBundleContext();

        assertNotNull(bc, "No context found.");
        assertEquals(1, bc.getBundles().length, "Wrong number of bundles.");

        Atomos runtime = bc.getService(
            bc.getServiceReference(Atomos.class));

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
        runtime = Atomos.newAtomos();
        implContent = runtime.getBootLayer().findAtomosContent(BSN_SERVICE_IMPL).get();

        testFramework = runtime.newFramework(Atomos.getConfiguration(args));
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

    @Test
    void testModuleNameBSNDiffer(@TempDir Path storage) throws BundleException
    {
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        // make sure the contract names are correct
        Module contractModule = Echo.class.getModule();
        Bundle contractBundle = FrameworkUtil.getBundle(Echo.class);
        assertEquals(BSN_CONTRACT, contractBundle.getSymbolicName(),
            "Wrong BSN for contract bundle.");
        assertEquals(Echo.class.getPackageName(), contractModule.getName(),
            "Wrong module name for contract module.");

        // make sure the bundle wiring reflect the mapping correctly using the BSN
        Bundle testBundle = FrameworkUtil.getBundle(ModulepathLaunch.class);
        BundleWiring testWiring = testBundle.adapt(BundleWiring.class);
        assertTrue(
            testWiring.getRequiredWires(BundleNamespace.BUNDLE_NAMESPACE).stream() //
                .filter((w) -> BSN_CONTRACT.equals( //
                    w.getCapability().getAttributes().get( //
                        BundleNamespace.BUNDLE_NAMESPACE))) //
                .findFirst().isPresent(),
            "No wire for " + BSN_CONTRACT);
    }

    @Test
    void testUnmodifiableExistingHeaders(@TempDir Path storage) throws BundleException
    {
        AtomicBoolean fail = new AtomicBoolean(true);
        HeaderProvider attemptModification = (location, headers) -> {
            try
            {
                headers.put(Constants.BUNDLE_SYMBOLICNAME, "should.fail");
                fail.set(true);
            }
            catch (UnsupportedOperationException e)
            {
                // expected
                fail.set(false);
            }
            return Optional.empty();
        };
        testFramework = Atomos.newAtomos(attemptModification).newFramework(
            Map.of(Constants.FRAMEWORK_STORAGE, storage.toFile().getAbsolutePath()));
        testFramework.start();
        if (fail.get())
        {
            Assertions.fail("Was able to modify the existing headers");
        }
    }

    @Test
    void testModuleWithCustomerHeader(@TempDir Path storage) throws BundleException, InterruptedException
    {
        HeaderProvider provider = (location, headers) -> {
            headers = new HashMap<>(headers);
            headers.put("X-TEST", location);
            return Optional.of(headers);
        };
        testFramework = getFramework(null, provider,
                Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());

        // make sure the contract names are correct
        Module contractModule = Echo.class.getModule();
        Bundle contractBundle = FrameworkUtil.getBundle(Echo.class);
        assertEquals(BSN_CONTRACT, contractBundle.getSymbolicName(),
                "Wrong BSN for contract bundle.");
        assertEquals(Echo.class.getPackageName(), contractModule.getName(),
                "Wrong module name for contract module.");

        assertEquals(contractBundle.getLocation(), "atomos:" + contractBundle.getHeaders().get("X-TEST"));

        testFramework.stop();
        testFramework.waitForStop(10000);

        // Bundles should already be installed, disable auto-install option
        // and check the provider is still used to provide the custom header
        // for the already installed bundle from persistence
        testFramework = Atomos.newAtomos(Map.of(Atomos.ATOMOS_CONTENT_INSTALL, "false"),
            provider).newFramework(
                Map.of(Constants.FRAMEWORK_STORAGE, storage.toFile().getAbsolutePath()));
        testFramework.start();
        Bundle contractBundle2 = FrameworkUtil.getBundle(Echo.class);
        assertNotEquals(contractBundle, contractBundle2, "Expecting new bundle.");
        assertEquals(contractBundle.getLocation(),
            "atomos:" + contractBundle.getHeaders().get("X-TEST"));
    }

    @Test
    void testHeaderProviderChangeBSN(@TempDir Path storage) throws BundleException
    {
        final String CHANGED_BSN = "changed.bsn";
        HeaderProvider changeBSN = (location, headers) -> {
            if (BSN_CONTRACT.equals(headers.get(Constants.BUNDLE_SYMBOLICNAME)))
            {
                headers = new HashMap<>(headers);
                headers.put(Constants.BUNDLE_SYMBOLICNAME, CHANGED_BSN);
                headers.put(Constants.BUNDLE_VERSION, "100");
                return Optional.of(headers);
            }
            return Optional.empty();
        };
        testFramework = getFramework(null, changeBSN,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());
        BundleContext bc = testFramework.getBundleContext();
        Atomos atomos = bc.getService(bc.getServiceReference(Atomos.class));

        // make sure the contract names are correct
        Module contractModule = Echo.class.getModule();
        Bundle contractBundle = FrameworkUtil.getBundle(Echo.class);
        assertEquals(CHANGED_BSN, contractBundle.getSymbolicName(),
            "Wrong BSN for contract bundle.");
        assertEquals(Version.valueOf("100"), contractBundle.getVersion());
        assertEquals(Echo.class.getPackageName(), contractModule.getName(),
            "Wrong module name for contract module.");

        // make sure the bundle wiring reflect the mapping correctly using the BSN
        Bundle testBundle = FrameworkUtil.getBundle(ModulepathLaunch.class);
        BundleWiring testWiring = testBundle.adapt(BundleWiring.class);
        assertTrue(testWiring.getRequiredWires(BundleNamespace.BUNDLE_NAMESPACE).stream() //
            .filter((w) -> CHANGED_BSN.equals( //
                w.getCapability().getAttributes().get( //
                    BundleNamespace.BUNDLE_NAMESPACE))) //
            .findFirst().isPresent(), "No wire for " + CHANGED_BSN);

        atomos.getBootLayer().findAtomosContent(CHANGED_BSN).ifPresentOrElse((c) -> {
            assertEquals(CHANGED_BSN, c.getSymbolicName());
            assertEquals(Version.valueOf("100"), c.getVersion());
        }, () -> {
            fail("Could not find the content: " + CHANGED_BSN);
        });
    }

    @Test
    void testMultiParentResolve(@TempDir Path storage) throws BundleException
    {
        testFramework = getFramework(null,
            Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath());
        final BundleContext bc = testFramework.getBundleContext();
        assertNotNull(bc, "No context found.");

        final Atomos atomos = bc.getService(
            bc.getServiceReference(Atomos.class));
        AtomosLayer layer1 = installChild(atomos, "layer1",
            List.of(atomos.getBootLayer()), TESTBUNDLES_DEPENDENCY_A);
        AtomosLayer layer2 = installChild(atomos, "layer2",
            List.of(atomos.getBootLayer()), TESTBUNDLES_DEPENDENCY_B);

        AtomosLayer layer3 = installChild(atomos, "layer3", List.of(layer1, layer2),
            TESTBUNDLES_DEPENDENT_X);
        assertEquals(List.of(layer1, layer2), layer3.getParents(), "Wrong parents.");

        assertEquals(Set.of(layer3), layer1.getChildren(), "Wrong children for layer1.");
        assertEquals(Set.of(layer3), layer2.getChildren(), "Wrong children for layer2.");

        Bundle dependentX = layer3.getAtomosContents().iterator().next().getBundle();

        // uninstall one of the parents
        layer2.uninstall();

        assertEquals(Bundle.UNINSTALLED, dependentX.getState(),
            "dependentX bundle should be uninstalled.");

        // should result in uninstalling layer3
        assertEquals(Set.of(), layer1.getChildren(), "Wrong children for layer1.");
        assertEquals(Set.of(), layer2.getChildren(), "Wrong children for layer2.");
    }

    private AtomosLayer installChild(Atomos atomos, String layerName,
        List<AtomosLayer> parents, String... moduleNames) throws BundleException
    {
        AtomosLayer layer = atomos.addLayer(parents, layerName, LoaderType.SINGLE,
            findModulePaths(moduleNames));

        startBundles(layer);
        return layer;
    }

    private void startBundles(AtomosLayer layer) throws BundleException
    {
        List<Bundle> bundles = new ArrayList<>();
        for (final AtomosContent atomosBundle : layer.getAtomosContents())
        {
            bundles.add(atomosBundle.install("child"));
        }
        for (final Bundle b : bundles)
        {
            try
            {
                b.start();
            }
            catch (BundleException e)
            {
                // Java 11 has a bug with automatic modules
                // this causes org.osgi.framework to not be available
                // when using SINGLE or MULTIPLE built-in loaders
                if ((layer.getLoaderType() == LoaderType.MANY
                    || layer.getLoaderType() == LoaderType.SINGLE)//
                    && Runtime.version().feature() == 11 //
                    && b.getSymbolicName().equals(TESTBUNDLES_DEPENDENT_X))
                {
                    // ignore;
                }
                else
                {
                    throw e;
                }
            }
        }
    }

    private Path[] findModulePaths(String... moduleNames)
    {
        final File modules = new File("target/modules");
        assertTrue(modules.isDirectory(), "Modules directory does not exist: " + modules);
        List<Path> found = new ArrayList<>();
        for (File candidate : modules.listFiles())
        {
            for (String name : moduleNames)
            {
                if (candidate.getName().startsWith(name))
                {
                    found.add(candidate.toPath());
                    break;
                }
            }
        }
        if (found.size() != moduleNames.length)
        {
            fail("Did not find all modules: " + moduleNames);
        }
        return found.toArray(new Path[0]);
    }
}
