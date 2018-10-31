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
package org.atomos.classpath.service.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

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

public class ClasspathLaunchTest {
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
	public void testClassPathServices() throws BundleException, InvalidSyntaxException {
		ClasspathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ClasspathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		Bundle[] bundles = bc.getBundles();
		assertTrue("No bundles: " + Arrays.toString(bundles), bundles.length > 0);
		for (Bundle b : bundles) {
			System.out.println(b.getBundleId() + " " + b.getLocation() + ": " + b.getSymbolicName() + ": " + getState(b));
		}
		checkServices(bc, 4);
		assertNull("Found a ModuleLayer.", bc.getService(bc.getServiceReference(AtomosRuntime.class)).getBootLayer().getModuleLayer().orElse(null));
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
