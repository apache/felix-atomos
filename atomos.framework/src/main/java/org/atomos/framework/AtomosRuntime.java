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

import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * The Atomos runtime provides a {@link FrameworkFactory} for constructing OSGi
 * Framework instances. The Atomos Framework instance will have a set of Atomos
 * bundles discovered and installed automatically when the Atomos Framework is
 * {@link Framework#init() initialized}. The Atomos bundles will be loaded from
 * the Java modules included the module path and will use the class loader
 * provided by the Java module system. This implies that the normal protection
 * provided by the OSGi module layer will not be available to the code loaded
 * out of Atomos bundles. Code in one boot bundle will be able to access and
 * load code from other boot bundles even when the package is not exported.
 * Other limitations are also imposed because a single class loader is used to
 * load all boot bundles. For example, multiple versions of a package cannot be
 * supported because the class loader can only define a single package of a
 * given name.
 * <p>
 * Atomos bundles are discovered using the module layer which loads the Atomos
 * framework. This is typically the boot layer. Atomos bundles are discovered by
 * searching the current module layer for for the available modules as well as
 * the modules available in the parent layers. These Atomos bundles will be
 * installed with a location prefix "atomos:".
 * <p>
 * The Atomos Framework can be created using the OSGi launcher APIs like a
 * normal OSGi Framework or for more advanced scenarios the Atomos runtime can
 * be used to setup additional module layer configurations before launching the
 * framework.
 * <p>
 * The following will launch using the standard {@link FrameworkFactory}:
 * 
 * <pre>
 * {@code Map<String, String>} config = getConfiguration();
 * config.put(Constants.FRAMEWORK_SYSTEMPACKAGES, "");
 * Framework framework = ServiceLoader.load(FrameworkFactory.class).iterator().next().newFramework(config);
 * framework.start();
 * </pre>
 * 
 * Note that the {@link Constants#FRAMEWORK_SYSTEMPACKAGES} must be set to an
 * empty value to prevent discovery of packages from the
 * {@link ModuleLayer#boot() boot} layer. Without doing this the OSGi R7
 * framework will discover all modules from the boot layer and assume they all
 * should be exported by the system bundle. That is normally desired so the
 * packages available from the boot layer (typically the modules provided by the
 * Java platform itself) can be available for import from bundles installed into
 * the framework. But with Atomos you may load all your bundles within the boot
 * layer itself. In that case you obviously do not want all the packages from
 * the bundles in the boot layer also exported by the system bundle. Setting
 * {@link Constants#FRAMEWORK_SYSTEMPACKAGES} to the empty value will prevent
 * this from happening. Atomos still needs to make the packages exported by the
 * Java platform modules available for import. In order to do this Atomos will
 * create an {@link AtomosBundleInfo Atomos bundle} for each module contained in
 * the {@link ModuleLayer#boot() boot} layer.
 * <p>
 * The following code uses the AtomosRuntime to first create a child layer which
 * is then used to load more modules in addition to the ones included on the
 * module path.
 * 
 * <pre>
 * ModuleLayer thisLayer = getClass().getModule().getLayer();
 * Configuration thisConfig = thisLayer.configuration();
 * 
 * // Find all the modules in a directory and use all of them
 * // as roots because we want to load them all
 * File modulesDir = getModulesDir();
 * ModuleFinder finder = ModuleFinder.of(modulesDir.toPath());
 * Set<String> roots = finder.findAll().stream().map((r) -> r.descriptor().name()).collect(Collectors.toSet());
 * 
 * // Resolve the configuration with all the roots
 * Configuration modulesConfig = Configuration.resolve(ModuleFinder.of(), List.of(thisConfig), finder, roots);
 * atomosRuntime.addLayer(modulesConfig);
 *
 * // The Atomos runtime must be used to create the framework in order
 * // use the additional children layers added
 * Framework framework = atomosRuntime.createFramework(frameworkConfig);
 * framework.start();
 * </pre>
 * 
 * By default all Atomos bundles will be installed and started in the OSGi
 * framework when the framework is started. If {@link #ATOMOS_BUNDLE_INSTALL
 * atomos.bundle.install} is set to <code>false</code> in the framework
 * configuration then the boot bundles will not be installed by default. In that
 * case the {@link AtomosBundleInfo#install(String)} method can be used to
 * selectively install Atomos bundles. If {@link #ATOMOS_BUNDLE_START
 * atomos.bundle.start} is set to <code>false</code> in the framework
 * configuration then the Atomos bundles will not be started by default. The
 * system.bundle of the initialized framework will also have an AtomosRuntime
 * service registered with its bundle context.
 */
public interface AtomosRuntime {
	/**
	 * If set to false then the Atomos bundles will not be automatically installed.
	 * Default is true.
	 */
	String ATOMOS_BUNDLE_INSTALL = "atomos.bundle.install";
	/**
	 * If set to false then the Atomos bundles will not be marked for start. Default
	 * is true.
	 */
	String ATOMOS_BUNDLE_START = "atomos.boot.bundle.start";
	/**
	 * The initial bundle start level to set before installing the Atomos bundles at
	 * {@link Framework#init() initialization}.
	 */
	String ATOMOS_INITIAL_BUNDLE_START_LEVEL = "atomos.initial.bundle.startlevel";

	/**
	 * A configuration option used by {@link #launch(Map)} which can be used to
	 * configuration a modules folder to load additional Atomos bundles from.
	 */
	String ATOMOS_MODULES_DIR = "atomos.modules";

	/**
	 * Returns the Atomos bundle info that is installed at the specified location
	 * 
	 * @param bundleLocation the bundle location.
	 * @return the Atomos bundle with the specified location or {@code null} if no
	 *         Atomos bundle is installed at the location.
	 */
	AtomosBundleInfo getAtomBundle(String bundleLocation);

	/**
	 * Adds a {@link Configuration} to the Atomos runtime. This can be done before
	 * or after the Atomos framework has been {@link #createFramework(Map) created}.
	 * If done before framework creation then the Atomos bundles found will be be
	 * automatically installed and started according to the
	 * {@link #ATOMOS_BUNDLE_INSTALL} and {@link #ATOMOS_BUNDLE_START} settings.
	 * <p>
	 * If the {@link Configuration#parents() parent} configurations do not already
	 * have existing Atomos Layers associated with them then they will also be created.
	 * All Atomos Layers created will use the specified name.
	 * 
	 * @param layerConfig the configuration to use for the new layer
	 * @param name the name of the new layer (and any new parent layers created), may be {@code null}.
	 * @return The Atomos layer that got created
	 */
	AtomosLayer addLayer(Configuration layerConfig, String name);

	/**
	 * The initial boot Atomos layer
	 * 
	 * @return the boot Atomos layer
	 */
	AtomosLayer getBootLayer();

	/**
	 * Creates a new {@link Framework} with the specified framework configuration.
	 * 
	 * @param frameworkConfig the framework configuraiton
	 * @return
	 */
	Framework createFramework(Map<String, String> frameworkConfig);

	/**
	 * A main method that can be used by executable jars to initialize and start an
	 * Atomos Framework. Each string in the arguments array may contain a key=value
	 * pair that will be used for the framework configuration.
	 * 
	 * @param args the args will be converted into a {@code Map<String, String>} to
	 *             use as configuration parameters for the OSGi Framework.
	 * @throws BundleException when an error occurs
	 */
	static void main(String[] args) throws BundleException {
		launch(getConfiguration(args));
	}

	/**
	 * Convenience method that creates an AtomosRuntime in order to load the Atomos
	 * bundles contained in the module path in the boot layer. It will also look for
	 * additional modules to load into a child layer in a modules folder. The path
	 * to the modules folder can be configured by using the
	 * {@link #ATOMOS_MODULES_DIR} launch option. If the {@link #ATOMOS_MODULES_DIR}
	 * option is not specified in the frameworkConfig then the default will try to
	 * determine the location on disk of the atomos.framework module and look for a
	 * folder called "modules". If the location of the atomos.framework module
	 * cannot be determined then no additional modules folder will be searched.
	 * 
	 * @param frameworkConfig the framework configuration
	 * @return a new Atomos framework instance which has been started.
	 * @throws BundleException if an error occurred creating and starting the Atomos
	 *                         framework
	 */
	static Framework launch(Map<String, String> frameworkConfig) throws BundleException {
		AtomosRuntime atomosRuntime = createAtomRuntime();

		ModuleLayer thisLayer = AtomosRuntime.class.getModule().getLayer();
		if (thisLayer != null) {
			// Get the ResolvedModule for the launcher module
			Configuration config = thisLayer.configuration();
			File modulesDir = getModulesDir(config, frameworkConfig);
			if (modulesDir != null) {
				// Find all the modules and use all of them as roots because we want to load
				// them all
				ModuleFinder finder = ModuleFinder.of(modulesDir.toPath());
				Set<String> roots = finder.findAll().stream().map((r) -> r.descriptor().name()).collect(Collectors.toSet());
	
				// Resolve the configuration with all the roots
				Configuration modulesConfig = Configuration.resolve(ModuleFinder.of(), List.of(config), finder, roots);
				atomosRuntime.addLayer(modulesConfig, "modules");
			}
		}
		Framework framework = atomosRuntime.createFramework(frameworkConfig);
		framework.start();
		return framework;
	}

	static private File getModulesDir(Configuration layerConfig, Map<String, String> frameworkConfig) {
		String modulesDirPath = frameworkConfig.get(ATOMOS_MODULES_DIR);
		File modulesDir = null;
		if (modulesDirPath != null) {
			// use the configured modules dir
			modulesDir = new File(modulesDirPath);
		} else {
			ResolvedModule resolved = layerConfig.findModule(AtomosRuntime.class.getModule().getName()).get();
			URI location = resolved.reference().location().get();
			if (location.getScheme().equals("file")) {
				// Use the module location as the relative base to locate the modules folder
				File thisModuleFile = new File(location);
				File candidate = new File(thisModuleFile.getParent(), "modules");
				modulesDir = candidate;
			}
		}
		return modulesDir != null && modulesDir.isDirectory() ? modulesDir : null;
	}

	/**
	 * Converts a string array into a {@code  Map<String,String>}
	 * 
	 * @param args the arguments where each element has key=string value, the key
	 *             cannot contain an '=' (equals) character.
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

	/**
	 * creates a new AtomosRuntime that can be used to create a new Atomos framework
	 * instance. If Atomos is running as a Java Module then this AtomosRuntime can
	 * be used to create additional layers by using the
	 * {@link #addLayer(Configuration)} method. If the additional layers are added
	 * before {@link #createFramework(Map) creating} and and {@link Framework#init()
	 * initializing} the framework then the Atomos bundles found in the added layers
	 * will be automatically installed and started according to the
	 * {@link #ATOMOS_BUNDLE_INSTALL} and {@link #ATOMOS_BUNDLE_START} options.
	 * <p>
	 * Note that this AtomosRuntime {@link #createFramework(Map)} must be used for a
	 * new {@link Framework framework} instance to use the layers added to this
	 * AtomosRuntime.
	 * 
	 * @return a new AtomosRuntime.
	 */
	static AtomosRuntime createAtomRuntime() {
		ServiceLoader<AtomosRuntime> loader;
		if (AtomosRuntime.class.getModule().getLayer() == null) {
			loader = ServiceLoader.load(AtomosRuntime.class, AtomosRuntime.class.getClassLoader());
		} else {
			loader = ServiceLoader.load( //
					AtomosRuntime.class.getModule().getLayer(), //
					AtomosRuntime.class);
		}
		return loader.findFirst() //
				.orElseThrow(() -> new RuntimeException("No AtomosRuntime implementation found."));
	}
}
