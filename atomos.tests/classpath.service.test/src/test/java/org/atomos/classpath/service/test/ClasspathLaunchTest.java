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
package org.atomos.classpath.service.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.atomos.framework.AtomosRuntime.LoaderType;
import org.atomos.framework.base.AtomosCommands;
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
import org.osgi.framework.FrameworkUtil;
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
	public void testClassPathGogo() throws BundleException, InvalidSyntaxException {

		ClasspathLaunch.main(
				new String[] { Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath() });
		testFramework = ClasspathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();

		String filter = "(osgi.command.scope=atomos)";

		Collection<ServiceReference<AtomosCommands>> serviceReferences = bc
				.getServiceReferences(AtomosCommands.class, filter);

		assertEquals(1, serviceReferences.size());
		AtomosCommands ac = bc.getService(serviceReferences.iterator().next());
		assertNotNull("AtomosCommands required", ac);
		ac.list();

	}

	@Test
	public void testClassPathServices() throws BundleException, InvalidSyntaxException {
		ClasspathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ClasspathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);
		checkBundleStates(bc.getBundles());

		checkServices(bc, 4);
		AtomosRuntime runtime = getRuntime(bc);
		assertNull("Found a ModuleLayer.", runtime.getBootLayer().adapt(ModuleLayer.class).orElse(null));
	}

	private AtomosRuntime getRuntime(BundleContext bc) {
		ServiceReference<AtomosRuntime> ref = bc.getServiceReference(AtomosRuntime.class);
		assertNotNull("No reference found.", ref);
		AtomosRuntime runtime = bc.getService(ref);
		assertNotNull("No service found.", runtime);
		return runtime;
	}

	@Test
	public void testInvalidCreateLayer() throws BundleException {
		AtomosRuntime runtime = AtomosRuntime.newAtomosRuntime();
		try {
			runtime.addLayer(List.of(runtime.getBootLayer()), "invalid", LoaderType.OSGI, storage);
			fail("Expected exception when addLayer is called.");
		} catch (UnsupportedOperationException e) {
			// expected
		}
	}

	@Test
	public void testFindBundle() throws BundleException {
		ClasspathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ClasspathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		AtomosRuntime runtime = getRuntime(bc);
		assertFindBundle("java.base", runtime.getBootLayer(), runtime.getBootLayer(), true);
		assertFindBundle("service.impl", runtime.getBootLayer(), runtime.getBootLayer(), true);
		assertFindBundle("service.impl.a", runtime.getBootLayer(), runtime.getBootLayer(), true);
		assertFindBundle("not.found", runtime.getBootLayer(), null, false);
	}

	@Test
	public void testGetEntry() throws BundleException {
		ClasspathLaunch.main(new String[] {Constants.FRAMEWORK_STORAGE + '=' + storage.toFile().getAbsolutePath()});
		testFramework = ClasspathLaunch.getFramework();
		BundleContext bc = testFramework.getBundleContext();
		assertNotNull("No context found.", bc);

		AtomosRuntime runtime = getRuntime(bc);
		Bundle b = runtime.getBundle(assertFindBundle("service.impl.a", runtime.getBootLayer(), runtime.getBootLayer(), true));
		assertNotNull("No bundle found.", b);
		URL mf = b.getEntry("/META-INF/MANIFEST.MF");
		assertNotNull("No manifest found.", mf);
		mf = b.getEntry("META-INF/MANIFEST.MF");
		assertNotNull("No manifest found.", mf);
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
		Bundle b = FrameworkUtil.getBundle(service.getClass());
		assertEquals("Wrong bundle.", ref.getBundle(), b);
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

	private void checkBundleStates(Bundle[] bundles) {
		assertTrue("No bundles: " + Arrays.toString(bundles), bundles.length > 0);
		for (Bundle b : bundles) {
			String msg = b.getBundleId() + " " + b.getLocation() + ": " + b.getSymbolicName() + ": " + getState(b);
			System.out.println(msg);
			int expected;
			if ("osgi.annotation".equals(b.getSymbolicName()) || "org.osgi.service.component.annotations".equals(b.getSymbolicName()) || b.getSymbolicName().startsWith("org.eclipse.jdt.junit")) {
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
}
