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
package org.atomos.framework;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

public class AtomosFrameworkFactoryTest {
	private Path storage;
	private Framework testFramework;

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
	public void testFactory() throws BundleException {
		ServiceLoader<ConnectFrameworkFactory> loader = ServiceLoader.load(getClass().getModule().getLayer(), ConnectFrameworkFactory.class);
		assertNotNull("null loader.", loader);

		List<ConnectFrameworkFactory> factories = new ArrayList<>();
		loader.forEach((f) -> factories.add(f));
		assertFalse("No factory found.", factories.isEmpty());

		ConnectFrameworkFactory factory = factories.get(0);
		assertNotNull("null factory.", factory);

		Map<String, String> config = Map.of(Constants.FRAMEWORK_STORAGE, storage.toFile().getAbsolutePath());
		testFramework = factory.newFramework(config, AtomosRuntime.newAtomosRuntime().newConnectFramework());
		doTestFramework(testFramework);
	}

	@Test
	public void testRuntime() throws BundleException {
		AtomosRuntime runtime = AtomosRuntime.newAtomosRuntime();
		Map<String, String> config = Map.of(Constants.FRAMEWORK_STORAGE, storage.toFile().getAbsolutePath());
		testFramework = runtime.newFramework(config);
		doTestFramework(testFramework);
	}

	private void doTestFramework(Framework testFramework) throws BundleException {
		testFramework.start();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		Bundle[] bundles = bc.getBundles();

		assertEquals("Wrong number of bundles.", ModuleLayer.boot().modules().size(), bundles.length);

		for (Bundle b : bundles) {
			String msg = b.getLocation() + ": " + b.getSymbolicName() + ": " + getState(b);
			System.out.println(msg);
			int expected;
			if ("osgi.annotation".equals(b.getSymbolicName())) {
				expected = Bundle.INSTALLED;
			} else {
				expected = Bundle.ACTIVE;
			}
			assertEquals("Wrong bundle state for bundle: " + msg, expected, b.getState());
		}
		Bundle javaLang = FrameworkUtil.getBundle(String.class);
		assertNotNull("No bundle found.", javaLang);
		assertEquals("Wrong bundle name.", String.class.getModule().getName(), javaLang.getSymbolicName());
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
