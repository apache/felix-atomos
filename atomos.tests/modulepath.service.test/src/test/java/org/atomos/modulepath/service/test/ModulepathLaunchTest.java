/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.atomos.modulepath.service.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
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

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.atomos.framework.AtomosRuntime.LoaderType;
import org.atomos.service.contract.Echo;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;


public class ModulepathLaunchTest {
	private Path storage;
	private Framework testFramework;
	@Rule
	public TestName name = new TestName();

	@Before
	public void beforeTest() throws IOException {
		storage = Files.createTempDirectory("equinoxTestStorage");

	}

	@After
	public void afterTest() throws BundleException, InterruptedException, IOException {
		if (testFramework != null && testFramework.getState() == Bundle.ACTIVE) {
			testFramework.stop();
			testFramework.waitForStop(10000);
		}
	    Files.walk(storage)
	      .sorted(Comparator.reverseOrder())
	      .map(Path::toFile)
	      .forEach(File::delete);
	}

	@Test
	public void testModulePathServices() throws BundleException, InvalidSyntaxException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		checkBundleStates(bc.getBundles());

		checkServices(bc, 2);
	}

	private void checkBundleStates(Bundle[] bundles) {
		assertTrue("No bundles: " + Arrays.toString(bundles), bundles.length > 0);
		for (Bundle b : bundles) {
			String msg = b.getBundleId() + " " + b.getLocation() + ": " + b.getSymbolicName() + ": " + getState(b);
			System.out.println(msg);
			int expected;
			if ("osgi.annotation".equals(b.getSymbolicName()) || "org.osgi.service.component.annotations".equals(b.getSymbolicName())) {
				expected = Bundle.INSTALLED;
			} else {
				expected = Bundle.ACTIVE;
			}
			if (b.getState() != expected && expected == Bundle.ACTIVE) {
				// for debugging
				try {
					b.start();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
			assertEquals("Wrong bundle state for bundle: " + msg, expected, b.getState());
		}
	}

	@Test
	public void testModuleDirServices() throws BundleException, InvalidSyntaxException, InterruptedException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(), AtomosRuntime.ATOMOS_MODULES_DIR + "=target/modules"});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		checkBundleStates(bc.getBundles());

		checkServices(bc, 4);
	}

	@Test
	public void testLoadFromModule() throws BundleException, InvalidSyntaxException, InterruptedException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(), AtomosRuntime.ATOMOS_MODULES_DIR + "=target/modules"});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		checkBundleStates(bc.getBundles());

		ServiceReference<AtomosRuntime> atomosRef = bc.getServiceReference(AtomosRuntime.class);
		assertNotNull("No Atomos runtime.", atomosRef);

		AtomosRuntime atomos = bc.getService(atomosRef);
		assertNotNull("Null Atomos runtime.", atomos);
		AtomosLayer bootLayer = atomos.getBootLayer();
		assertNotNull("The boot layer is null.", bootLayer);
		Set<AtomosLayer> children = bootLayer.getChildren();
		assertNotNull("Null children.", children);
		assertEquals("Wrong number of children.", 1, children.size());

		AtomosLayer child = children.iterator().next();
		assertEquals("Wrong number of bundles.", 5, child.getAtomosBundles().size());
		Module serviceLibModule = null;
		for(AtomosBundleInfo atomosBundle : child.getAtomosBundles()) {
			if (atomosBundle.getSymbolicName().equals("service.lib")) {
				serviceLibModule = atomosBundle.adapt(Module.class).get();
			}
		}
		try {
			Class<?> clazz = serviceLibModule.getClassLoader().loadClass("org.atomos.service.lib.SomeUtil");
			assertNotNull("Null class from loadClass.", clazz);
		} catch (Exception e) {
			fail("Failed to find class: " + e.getMessage());
		}
	}

	private ClassLoader getCLForResourceTests() throws BundleException  {
	    ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + 
	            storage.toFile().getAbsolutePath(), AtomosRuntime.ATOMOS_MODULES_DIR + "=target/modules/resource.a-0.0.1-SNAPSHOT.jar"});
	    testFramework = ModulepathLaunch.getFramework();
	    BundleContext bc = testFramework.getBundleContext();
	    checkBundleStates(bc.getBundles());

	    ServiceReference<AtomosRuntime> atomosRef = bc.getServiceReference(AtomosRuntime.class);
	    AtomosRuntime atomos = bc.getService(atomosRef);
	    AtomosLayer bootLayer = atomos.getBootLayer();
	    Set<AtomosLayer> children = bootLayer.getChildren();
	    assertNotNull("Null children.", children);
	    assertEquals("Wrong number of children.", 1, children.size());

	    AtomosLayer child = children.iterator().next();
	    assertEquals("Wrong number of bundles.", 1, child.getAtomosBundles().size());
	    Module serviceLibModule = null;
	    for(AtomosBundleInfo atomosBundle : child.getAtomosBundles()) {
	        if (atomosBundle.getSymbolicName().equals("resource.a")) {
	            serviceLibModule = atomosBundle.adapt(Module.class).get();
	        }
	    }
	    return serviceLibModule.getClassLoader();
	}
	
	@Test
	public void testResourceGetMissingResource() throws ClassNotFoundException, BundleException {
	    try {
	        Class<?> clazz = getCLForResourceTests().loadClass("org.atomos.resource.Clazz");
	        URL u = clazz.getResource("/META-TEXT/noFile.txt");
	        assertNull("get of non-existent resource should return null.", u);
	    } catch (ClassNotFoundException e) {
	        fail("Failed to find class: " + e.getMessage());
	    }
	}

	@Test
	public void testResourceLoadResource() throws ClassNotFoundException, BundleException {
	    try {
	        Class<?> clazz = getCLForResourceTests().loadClass("org.atomos.resource.Clazz");
	        URL resc = clazz.getResource("/META-TEXT/file.txt");
	        assertTrue("Expected URL, got null ", resc!=null); 
	        assertTrue("Could not get resource from URL", resc.getFile() != null);
	    } catch (ClassNotFoundException e) {
	        fail("Failed to find class: " + e.getMessage());
	    }
	}

	@Test
	public void testResourcePackagedResource() throws ClassNotFoundException, BundleException, IOException {
	    try {
	        Class<?> clazz = getCLForResourceTests().loadClass("org.atomos.resource.Clazz");
	        URL resc = clazz.getResource("file.txt");
	        assertTrue("Expected URL, got null ", resc!=null); 	        
	        assertTrue("Incorrect contents from URL", new BufferedReader(
	        		new InputStreamReader(resc.openStream())).readLine().equals("/org/atomos/resource/file.txt"));
	    } catch (ClassNotFoundException e) {
	        fail("Failed to find class: " + e.getMessage());
	    }
	}

	@Test
	public void testResourceRootResource() throws ClassNotFoundException, BundleException, IOException {
	    try {
	        Class<?> clazz = getCLForResourceTests().loadClass("org.atomos.resource.Clazz");
	        URL resc = clazz.getResource("/file.txt");
	        assertTrue("Expected URL, got null ", resc!=null); 	        
	        assertTrue("Incorrect contents from URL", new BufferedReader(
	        		new InputStreamReader(resc.openStream())).readLine().equals("/file.txt"));
	    } catch (ClassNotFoundException e) {
	        fail("Failed to find class: " + e.getMessage());
	    }
	}

	@Test
	public void testAddNewLayers() throws BundleException, InvalidSyntaxException, InterruptedException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(), AtomosRuntime.ATOMOS_MODULES_DIR + "=target/modules"});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		Bundle[] bundles = bc.getBundles();
		int originalNum = bundles.length;
		assertTrue("No bundles: " + Arrays.toString(bundles), bundles.length > 0);

		checkServices(bc, 4);

		AtomosRuntime atomosRuntime = bc.getService(bc.getServiceReference(AtomosRuntime.class));

		Collection<AtomosLayer> layers = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			layers.add(installChild(atomosRuntime.getBootLayer(), "services-" + i, atomosRuntime, LoaderType.OSGI));
		}
		checkServices(bc, 44);

		checkBundleStates(bc.getBundles());

		List<Bundle> allChildBundles = layers.stream().flatMap((l) -> l.getAtomosBundles().stream()).
				map((a) -> atomosRuntime.getBundle(a)).filter(Objects::nonNull).collect(Collectors.toList());

		AtomosLayer firstChild = layers.iterator().next();
		Set<AtomosBundleInfo> firstChildInfos = firstChild.getAtomosBundles();

		List<Bundle> firstChildBundles = firstChildInfos.stream().map((a) -> atomosRuntime.getBundle(a)).filter(Objects::nonNull).collect(Collectors.toList());

		assertEquals("Wrong number of bundles in first child.", 5, firstChildBundles.size());
		firstChildBundles.forEach((b) -> {
			try {
				b.uninstall();
			} catch (BundleException e) {
				throw new RuntimeException(e);
			}
		});

		firstChildBundles.forEach((b) -> {
			assertNull("No AtomsBundle expected.", atomosRuntime.getAtomosBundle(b.getLocation()));
		});

		firstChildBundles = firstChildInfos.stream().map((a) -> atomosRuntime.getBundle(a)).filter(Objects::nonNull).collect(Collectors.toList());
		assertEquals("Wrong number of bundles in first child.", 0, firstChildBundles.size());

		layers.forEach((l) -> {
			try {
				l.uninstall();
			} catch (BundleException e) {
				throw new RuntimeException(e);
			}
		});
		checkServices(bc, 4);

		allChildBundles.forEach((b) -> {
			assertNull("No AtomsBundle expected.", atomosRuntime.getAtomosBundle(b.getLocation()));
		});

		assertEquals("Wrong number of final bundles.", originalNum, bc.getBundles().length);
	}

	@Test
	public void testPersistLayers() throws BundleException, InvalidSyntaxException, InterruptedException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		AtomosRuntime atomosRuntime1 = bc.getService(bc.getServiceReference(AtomosRuntime.class));
		installChild(atomosRuntime1.getBootLayer(), "SINGLE", atomosRuntime1, LoaderType.SINGLE);
		installChild(atomosRuntime1.getBootLayer(), "MANY", atomosRuntime1, LoaderType.MANY);
		installChild(atomosRuntime1.getBootLayer(), "OSGI", atomosRuntime1, LoaderType.OSGI);

		checkBundleStates(bc.getBundles());
		checkServices(bc, 8);

		testFramework.stop();
		testFramework.waitForStop(10000);

		// try starting the framework directly again
		testFramework.start();
		bc = testFramework.getBundleContext();

		AtomosLayer child1 = installChild(atomosRuntime1.getBootLayer(), "SINGLE2", atomosRuntime1, LoaderType.SINGLE);

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
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ModulepathLaunch.getFramework();
		bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		checkServices(bc, 8);

		AtomosRuntime atomosRuntime2 = bc.getService(bc.getServiceReference(AtomosRuntime.class));
		AtomosLayer bootLayer = atomosRuntime2.getBootLayer();
		assertEquals("Wrong number of children.", 3, bootLayer.getChildren().size());

		List<AtomosLayer> children = bootLayer.getChildren().stream().sorted((l1, l2) -> Long.compare(l1.getId(), l2.getId())).collect(Collectors.toList());
		checkLayer(children.get(0), LoaderType.SINGLE, 2);
		checkLayer(children.get(1), LoaderType.MANY, 3);
		checkLayer(children.get(2), LoaderType.OSGI, 4);

		// uninstall service.impl.a bundle from the first child
		children.iterator().next().getAtomosBundles().stream().
			map((a) -> atomosRuntime2.getBundle(a)).filter(Objects::nonNull).
				filter((b) -> b.getSymbolicName().equals("service.impl.a")).findFirst().orElseThrow().uninstall();
		checkServices(bc, 7);

		testFramework.stop();
		testFramework.waitForStop(10000);

		// startup with the option not to force install all atomos bundles
		ModulepathLaunch.main(new String[] {
				Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(),
				AtomosRuntime.ATOMOS_BUNDLE_INSTALL + "=false"
		});
		testFramework = ModulepathLaunch.getFramework();
		bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		checkServices(bc, 7);
	}

	@Test
	public void testLoaderType() throws BundleException, InvalidSyntaxException, InterruptedException, ClassNotFoundException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		AtomosRuntime atomosRuntime = bc.getService(bc.getServiceReference(AtomosRuntime.class));
		checkLoader(atomosRuntime, installChild(atomosRuntime.getBootLayer(), "OSGI", atomosRuntime, LoaderType.OSGI), LoaderType.OSGI);
		checkLoader(atomosRuntime, installChild(atomosRuntime.getBootLayer(), "SINGLE", atomosRuntime, LoaderType.SINGLE), LoaderType.SINGLE);
		checkLoader(atomosRuntime, installChild(atomosRuntime.getBootLayer(), "MANY", atomosRuntime, LoaderType.MANY), LoaderType.MANY);
	}

	@Test
	public void testFindBundle() throws BundleException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		AtomosRuntime atomosRuntime = bc.getService(bc.getServiceReference(AtomosRuntime.class));
		AtomosLayer child = installChild(atomosRuntime.getBootLayer(), "SINGLE", atomosRuntime, LoaderType.SINGLE);
		assertFindBundle("java.base", child, atomosRuntime.getBootLayer(), true);
		assertFindBundle("service.impl.a", child, child, true);
		assertFindBundle("service.impl", child, atomosRuntime.getBootLayer(), true);
		assertFindBundle("service.impl.a", atomosRuntime.getBootLayer(), null, false);
	}

	@Test
	public void testGetEntry() throws BundleException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		AtomosRuntime atomosRuntime = bc.getService(bc.getServiceReference(AtomosRuntime.class));
		AtomosLayer child = installChild(atomosRuntime.getBootLayer(), "SINGLE", atomosRuntime, LoaderType.SINGLE);
		Bundle b = atomosRuntime.getBundle(child.findAtomosBundle("service.impl.a").get());
		assertNotNull("No bundle found.", b);
		URL mf = b.getEntry("/META-INF/MANIFEST.MF");
		assertNotNull("No manifest found.", mf);
		mf = b.getEntry("META-INF/MANIFEST.MF");
		assertNotNull("No manifest found.", mf);
	}

	@Test
	public void testInstallDifferentPrefix() throws BundleException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		AtomosRuntime atomosRuntime = bc.getService(bc.getServiceReference(AtomosRuntime.class));
		AtomosLayer child = installChild(atomosRuntime.getBootLayer(), "SINGLE", atomosRuntime, LoaderType.SINGLE);
		AtomosBundleInfo ab = child.findAtomosBundle("service.impl.a").get();
		Bundle b = atomosRuntime.getBundle(ab);
		assertNotNull("No bundle found.", b);

		try {
			ab.install("shouldFail");
			fail("Should not be able to install with different prefix");
		} catch (BundleException e) {
			// expected
		}

		Bundle existing = ab.install("child");
		assertNotNull("No bundle.", existing);
		assertEquals("Existing bundle doesn't equal original.", b, existing);

		// now try to uninstall and use a different prefix
		existing.uninstall();
		b = ab.install("testPrefix");
		assertTrue("Wrong location prefix: " + b.getLocation(), b.getLocation().startsWith("testPrefix:"));
		b.start();
	}

	@Test
	public void testReferenceUser() throws BundleException, InvalidSyntaxException {
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		AtomosRuntime atomosRuntime = bc.getService(bc.getServiceReference(AtomosRuntime.class));
		AtomosLayer child = installChild(atomosRuntime.getBootLayer(), "testRef", atomosRuntime, LoaderType.MANY);
		checkServices(bc, 4);
		AtomosBundleInfo ab = child.findAtomosBundle("service.user").get();
		Bundle b = atomosRuntime.getBundle(ab);
		assertNotNull("No bundle found.", b);

		ServiceReference<?>[] refs = b.getRegisteredServices();
		assertNotNull("No services.", refs);
		assertEquals("Wrong number of services.", 1, refs.length);
		assertEquals("Wrong service.", Boolean.TRUE, refs[0].getProperty("echo.reference"));
	}

	private AtomosBundleInfo assertFindBundle(String name, AtomosLayer layer, AtomosLayer expectedLayer, boolean expectedToFind) {
		Optional<AtomosBundleInfo> result = layer.findAtomosBundle(name);
		if (expectedToFind) {
			assertTrue("Could not find bundle: " + name, result.isPresent());
			assertEquals("Wrong name", name, result.get().getSymbolicName());
			assertEquals("Wrong layer for bundle: " + name, expectedLayer, result.get().getAtomosLayer());
		} else {
			assertFalse("Found unexpected bundle: " + name, result.isPresent());
		}
		return result.orElse(null);
	}

	@Test
	public void testInvalidUseOfRuntime() throws BundleException, InterruptedException{
		ModulepathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ModulepathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		AtomosRuntime atomosRuntime = bc.getService(bc.getServiceReference(AtomosRuntime.class));
		
		try {
			atomosRuntime.newFramework(Map.of(Constants.FRAMEWORK_STORAGE, storage.toFile().getAbsolutePath()));
			fail();
		} catch (IllegalStateException e) {
			// expected
		}
	}

	private void checkLoader(AtomosRuntime runtime, AtomosLayer layer, LoaderType loaderType) throws ClassNotFoundException {
		Set<AtomosBundleInfo> atomosBundles = layer.getAtomosBundles();
		List<Class<?>> classes = new ArrayList<>();
		for (AtomosBundleInfo atomosBundle : atomosBundles) {
			Bundle b = runtime.getBundle(atomosBundle);
			assertNotNull("No bundle found: " + atomosBundle.getSymbolicName(), b);
			String name = getTestClassName(b);
			assertNotNull("No class name.", name);
			classes.add(b.loadClass(name));
		}
		Set<ClassLoader> classLoaders = new HashSet<>();
		for (Class<?> clazz : classes) {
			classLoaders.add(clazz.getClassLoader());
		}
		switch (loaderType) {
		case OSGI:
			for (ClassLoader classLoader : classLoaders) {
				 assertTrue("Class loader is not a BundleReference", classLoader instanceof BundleReference);
			}
			assertEquals("Wrong number of class loaders.", 5, classLoaders.size());
			break;
		case MANY :
			for (ClassLoader classLoader : classLoaders) {
				assertFalse("Class loader is a BundleReference", classLoader instanceof BundleReference);
			}
			assertEquals("Wrong number of class loaders.", 5, classLoaders.size());
			break;
		case SINGLE:
			assertEquals("Wrong number of class loaders.", 1, classLoaders.size());
			break;

		default:
			fail();
		}
	}

	private String getTestClassName(Bundle b) {
		switch (b.getSymbolicName()) {
		case "service.impl.a":
			return "org.atomos.service.impl.a.EchoImpl";
		case "service.impl.b":
			return "org.atomos.service.impl.b.EchoImpl";
		case "service.lib":
			return "org.atomos.service.lib.SomeUtil";
		case "service.user":
			return "org.atomos.service.user.EchoUser";
		case "resource.a":
			return "org.atomos.resource.Clazz";
		default:
			fail("Unknown");
		}
		return null;
	}

	private void checkLayer(AtomosLayer atomosLayer, LoaderType loaderType, int id) {
		assertEquals("Wrong id.", id, atomosLayer.getId());
		assertEquals("Wrong loaderType", loaderType, atomosLayer.getLoaderType());
		assertEquals("Wrong name.", loaderType.toString(), atomosLayer.getName());
	}

	private AtomosLayer installChild(AtomosLayer parent, String name, AtomosRuntime atomosRuntime, LoaderType loaderType) throws BundleException {
		File modules = new File("target/modules");
		assertTrue("Modules directory does not exist: " + modules, modules.isDirectory());

		AtomosLayer child = atomosRuntime.addLayer(List.of(parent), name, loaderType, modules.toPath());

		List<Bundle> bundles = new ArrayList<>();
		for (AtomosBundleInfo atomosBundle : child.getAtomosBundles()) {
			bundles.add(atomosBundle.install("child"));
		}
		for (Bundle b : bundles) {
			b.start();
		}

		return child;
	}

	private void checkServices(BundleContext bc, int expectedNumber) throws InvalidSyntaxException {
		ServiceReference<?>[] echoRefs = bc.getAllServiceReferences(Echo.class.getName(), null);
		assertNotNull("No Echo service ref found.", echoRefs);
		assertEquals("Wrong number of services.", expectedNumber, echoRefs.length);
		for (ServiceReference<?> ref : echoRefs) {
			Echo echo = (Echo) bc.getService(ref);
			assertNotNull("No Echo service found.", echo);
			assertEquals("Wrong Echo.", ref.getProperty("type") + " Hello!!", echo.echo("Hello!!"));
			checkClassBundle(echo, ref);
		}
	}

	private void checkClassBundle(Object service, ServiceReference<?> ref) {
		Class<?> serviceClass = service.getClass();	
		Bundle b = FrameworkUtil.getBundle(serviceClass);
		assertEquals("Wrong bundle.", ref.getBundle(), b);
		assertEquals("Wrong module name", b.getSymbolicName(), service.getClass().getModule().getName());
	}

	private String getState(Bundle b) {
		switch (b.getState()) {
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
