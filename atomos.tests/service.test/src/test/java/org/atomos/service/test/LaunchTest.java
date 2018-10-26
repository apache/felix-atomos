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
package org.atomos.service.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.atomos.service.contract.Echo;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;

public class LaunchTest {
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
		Launch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = Launch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		Bundle[] bundles = bc.getBundles();
		assertTrue("No bundles: " + Arrays.toString(bundles), bundles.length > 0);
		for (Bundle b : bundles) {
			System.out.println(b.getBundleId() + " " + b.getLocation() + ": " + b.getSymbolicName() + ": " + getState(b));
		}

		checkServices(bc, 2);
	}

	@Test
	public void testModuleDirServices() throws BundleException, InvalidSyntaxException {
		Launch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(), AtomosRuntime.ATOMOS_MODULES_DIR + "=target/modules"});
		testFramework = Launch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		Bundle[] bundles = bc.getBundles();
		assertTrue("No bundles: " + Arrays.toString(bundles), bundles.length > 0);
		for (Bundle b : bundles) {
			System.out.println(b.getBundleId() + " " + b.getLocation() + ": " + b.getSymbolicName() + ": " + getState(b));
		}

		checkServices(bc, 4);
	}

	@Test
	public void testModuleDirLoadFromModule() throws BundleException, InvalidSyntaxException {
		Launch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(), AtomosRuntime.ATOMOS_MODULES_DIR + "=target/modules"});
		testFramework = Launch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		Bundle[] bundles = bc.getBundles();
		assertTrue("No bundles: " + Arrays.toString(bundles), bundles.length > 0);
		for (Bundle b : bundles) {
			System.out.println(b.getBundleId() + " " + b.getLocation() + ": " + b.getSymbolicName() + ": " + getState(b));
		}
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
		assertEquals("Wrong number of bundles.", 3, child.getAtomosBundles().size());
		Module serviceLibModule = null;
		for(AtomosBundleInfo atomosBundle : child.getAtomosBundles()) {
			if (atomosBundle.getSymbolicName().equals("service.lib")) {
				serviceLibModule = atomosBundle.getModule().get();
			}
		}
		try {
			Class<?> clazz = serviceLibModule.getClassLoader().loadClass("org.atomos.service.lib.SomeUtil");
			assertNotNull("Null class from loadClass.", clazz);
		} catch (Exception e) {
			fail("Failed to find class: " + e.getMessage());
		}
	}

	@Test
	public void testAddNewLayer() throws BundleException, InvalidSyntaxException {
		Launch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath(), AtomosRuntime.ATOMOS_MODULES_DIR + "=target/modules"});
		testFramework = Launch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		Bundle[] bundles = bc.getBundles();
		int originalNum = bundles.length;
		assertTrue("No bundles: " + Arrays.toString(bundles), bundles.length > 0);

		checkServices(bc, 4);

		AtomosRuntime atomosRuntime = bc.getService(bc.getServiceReference(AtomosRuntime.class));

		Collection<AtomosLayer> layers = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			layers.add(installChild(atomosRuntime.getBootLayer(), "services-" + i, atomosRuntime));
		}
		checkServices(bc, 44);

		for (Bundle b : bc.getBundles()) {
			System.out.println(b.getBundleId() + " " + b.getLocation() + ": " + b.getSymbolicName() + ": " + getState(b));
		}
	
		layers.forEach((l) -> {
			try {
				l.uninstall();
			} catch (BundleException e) {
				throw new RuntimeException(e);
			}
		});
		checkServices(bc, 4);

		assertEquals("Wrong number of final bundles.", originalNum, bc.getBundles().length);
	}

	private AtomosLayer installChild(AtomosLayer parent, String name, AtomosRuntime atomosRuntime) throws BundleException {
		File modules = new File("target/modules");
		assertTrue("Modules directory does not exist: " + modules, modules.isDirectory());

		AtomosLayer child = atomosRuntime.addLayer(List.of(parent), name, modules.toPath());

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
		}
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
