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

import java.io.File;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.VersionRange;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
/**
 * The Atom runtime provides a {@link FrameworkFactory} for constructing OSGi Framework instances.
 * The Atom Framework instance will have a set of atomos bundles discovered and installed
 * automatically when the Atom Framework is {@link Framework#init() initialized}.
 * The atomos bundles will be loaded from a Java module and will use the class loader
 * provided by the Java module system. This implies that the normal protection provided 
 * by the OSGi module layer will
 * not be available to the code loaded out of atomos bundles.  Code in one boot bundle will
 * be able to access and load code from other boot bundles even when the package is not
 * exported.  Other limitations are also imposed because a single class loader is used
 * to load all boot bundles.  For example, multiple versions of a package cannot
 * be supported because the class loader can only define a single package of a given name.
 * <p>
 * Boot bundles are discovered using the class loader which loads the OSGi Boot implementation.
 * This is typically the Java application class loader. Boot bundles are discovered by 
 * searching the class path for bundle manifest resources (META-INF/MANIFEST.MF), any that contain
 * a {@link Constants#BUNDLE_SYMBOLICNAME Bundle-SymbolicName} header will be used as a boot
 * bundle. These boot bundles will be installed with a location prefix "osgiboot:".
 * 
 * <p>
 * The OSGi Boot Framework is created using the OSGi launcher APIs like a normal OSGi Framework:
 * <pre>
 * {@code Map<String, String>} config = getConfiguration();
 * Framework framework = ServiceLoader.load(FrameworkFactory.class).iterator().next().newFramework(config);
 * framework.start();
 * </pre>
 * 
 * By default this will have all boot bundles installed and started when the framework is started.
 * If {@link #ATOM_BUNDLE_INSTALL osgi.boot.bundle.install} is set to <code>false</code> in the framework
 * configuration then the boot bundles will not be installed by default. In that case the
 * {@link #installAtomBundle(String, VersionRange, String)} method can be used to selectively install
 * boot bundles.
 * If {@link #ATOM_BUNDLE_START osgi.boot.bundle.start} is set to <code>false</code> in the framework
 * configuration then the boot bundles will not be started by default.
 * The system.bundle of the initialized framework will also have an AtomosRuntime service registered with
 * its bundle context.
 * <p>
 * In order to {@link #BOOT_MULTIPLEX_CONTEXT multiplex} the system bundle context each framework instance
 * should be {@link Framework#init() initialized} and {@link Framework#start() started} from a thread group
 * with a unique name prefixed with {@link #BOOT_MULTIPLEX_GROUP_PREFIX osgi-boot-}.  The following
 * shows an example of starting two framework instances with a multiplexed context:
 * 
 * <pre>
 * ThreadGroup g1 = new ThreadGroup(AtomosRuntime.getNextThreadGroupName());
 * ExecutorService e1 = Executors.newSingleThreadExecutor((r) -&gt; { 
 *   return new Thread(g1, r);
 * });
 * {@code Map<String, String>} config1 = getConfiguration();
 * // configure multiplexing
 * config1.put(AtomosRuntime.BOOT_MULTIPLEX_CONTEXT, "true");
 * Framework f1 = ServiceLoader.load(FrameworkFactory.class).iterator().next().newFramework(config1);
 * e1.submit(() -&gt; f1.start());
 *
 * ThreadGroup g2 = new ThreadGroup(AtomosRuntime.getNextThreadGroupName());
 * ExecutorService e2 = Executors.newSingleThreadExecutor((r) -&gt; {
 *   return new Thread(g2, r);
 * });
 * {@code Map<String, String>} config2 = getConfiguration();
 * // configure multiplexing
 * config2.put(AtomosRuntime.BOOT_MULTIPLEX_CONTEXT, "true");
 * Framework f2 = ServiceLoader.load(FrameworkFactory.class).iterator().next().newFramework(config2);
 * e1.submit(() -&gt; f2.start());
 * </pre>
 *
 */
public interface AtomosRuntime {
	/**
	 * If set to false then the atomos bundles will not be automatically installed.  Default is true.
	 */
	String ATOM_BUNDLE_INSTALL = "atomos.bundle.install"; //$NON-NLS-1$
	/**
	 * If set to false then the boot bundles will not be marked for start.  Default is true.
	 */
	String ATOM_BUNDLE_START = "atomos.boot.bundle.start"; //$NON-NLS-1$
	/**
	 * The initial bundle start level to set before installing the boot bundles
	 */
	String ATOM_INITIAL_BUNDLE_START_LEVEL = "atomos.initial.bundle.startlevel"; //$NON-NLS-1$

	/**
	 * Returns the boot bundle info that is installed at the specified location
	 * @param bundleLocation the bundle location.
	 * @return the boot bundle with the specified location or {@code null}
	 * if no boot bundle is installed at the location.
	 */
	AtomosBundleInfo getAtomBundle(String bundleLocation);

	AtomosLayer addLayer(Configuration layerConfig);

	AtomosLayer getBootLayer();

	Framework createFramework(Map<String, String> frameworkConfig);

	/**
	 * A main method that can be used by executable jars to initialize and start
	 * an OSGi Atom Framework. Each string in the arguments array may contain a
	 * key=value pair that will be used for the framework configuration.
	 * @param args the args will be converted into a {@code Map<String, String>} to use as
	 * configuration parameters for the OSGi Framework.
	 * @throws BundleException when an error occurs
	 */
	static void main(String[] args) throws BundleException {
		launch(getConfiguration(args));
	}

	static Framework launch(Map<String, String> frameworkConfig) throws BundleException {
		ModuleLayer thisLayer = AtomosRuntime.class.getModule().getLayer();
		if (thisLayer == null) {
			throw new IllegalStateException("Must launch on the module path.");
		}
    	// Get the ResolvedModule for the launcher module
        Configuration config = thisLayer.configuration();
        ResolvedModule resolved = config.findModule(AtomosRuntime.class.getModule().getName()).get();
        URI location = resolved.reference().location().get();

        AtomosRuntime atomosRuntime = createAtomRuntime();

		if (location.getScheme().equals("file")) {
			// Use the module location as the relative base to locate the modules folder
			File thisModuleFile = new File(location);
			File modules = new File(thisModuleFile.getParent(), "modules");
			if (modules.isDirectory()) {
				// Find all the modules and use all of them as roots because we want to load
				// them all
				ModuleFinder finder = ModuleFinder.of(modules.toPath());
				Set<String> roots = finder.findAll().stream().map((r) -> r.descriptor().name())
						.collect(Collectors.toSet());

				// Resolve the configuration with all the roots
				Configuration modulesConfig = Configuration.resolve(ModuleFinder.of(), List.of(config), finder, roots);
				atomosRuntime.addLayer(modulesConfig);
			}
		}
		Framework framework = atomosRuntime.createFramework(frameworkConfig);
		framework.start();
		return framework;
	}
	/**
	 * Converts a string array into a {@code  Map<String,String>}
	 * @param args the arguments where each element has key=string value
	 * @return a map of the configuration specified by the args
	 */
	static Map<String, String> getConfiguration(String[] args) {
		Map<String, String> config = new HashMap<>();
		if (args != null) {
			for (String arg : args) {
				int equals = arg.indexOf('=');
				if (equals != -1) {
					String key = arg.substring(0, equals);
					String value = arg.substring(equals + 1);
					config.put(key, value);
				}
			}
		}
		return config;
	}

	static AtomosRuntime createAtomRuntime() {
		return ServiceLoader.load( //
				AtomosRuntime.class.getModule().getLayer(), //
				AtomosRuntime.class).findFirst().orElseThrow(() -> {
					throw new RuntimeException("No AtomosRuntime implementation found.");
				});
	}
}
