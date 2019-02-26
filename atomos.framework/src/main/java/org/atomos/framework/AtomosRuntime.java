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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.atomos.framework.base.AtomosRuntimeBase;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * The Atomos runtime extends {@link FrameworkFactory} for creating new OSGi
 * {@link Framework} instances with bundles loaded from the module or class
 * path. The Framework instance will have a set of bundles discovered and
 * installed automatically when the Framework is {@link Framework#init()
 * initialized}.
 * <p>
 * When loading Atomos from the module path the bundles will be loaded from the
 * Java modules included on the module path and will use the class loader
 * provided by the Java module system. This implies that the normal protection
 * provided by the OSGi module layer will not be available to the classes loaded
 * out of the module path bundles. Classes in one bundle will be able to load
 * classes from other bundles even when the package is not exported. But the
 * Java module system will provide protection against modules trying to execute
 * code from packages that are not exported. This means you may find cases where
 * a bundle can load a class from another bundle but then get runtime exceptions
 * when trying to execute methods on the class. Other limitations are also
 * imposed because a single class loader is used to load all bundles. For
 * example, multiple versions of a package cannot be supported because the class
 * loader can only define a single package of a given name.
 * <p>
 * When loading from the module path Atomos bundles are discovered using the
 * module layer which loads the Atomos framework. This is typically the boot
 * layer. Atomos bundles are discovered by searching the current module layer
 * for the available modules as well as the modules available in the parent
 * layers until the {@link ModuleLayer#empty() empty} layer is reached.
 * <p>
 * When loading Atomos from the class path (i.e as an unnamed module) the
 * bundles will be loaded from JARs included on the class path along side the
 * Atomos JARs and will use the application class loader provided by the JVM.
 * This also implies that the normal protection provided by the OSGi module
 * layer will not be available to the classes loaded out of the class path
 * bundles. But unlike when loading bundles from the module path, the Java
 * module system will not provide any protection against executing code from
 * other bundles from private packages. This mode also suffers from the typical
 * issues that arise if you have multiple JARs on the class path that provide
 * the same package. When in module path mode the JVM will fail to launch and
 * inform you that it detected duplicate packages from the modules on the module
 * path. But in class path mode the JVM does no such check, this leaves you open
 * to have unexpected shadowing if you have multple versions of the same package
 * on the class path.
 * <p>
 * When loading from the class path Atomos bundles are discovered by searching
 * the class path for bundle manifests (META-INF/MANIFEST.MF) resources and
 * discovering any that contain OSGI meta-data (e.g. Bundle-SymbolicName). The
 * boot layer is also searched and loads all boot modules as bundles similar to
 * when loading from the module path.
 * <p>
 * The Framework can be created using the {@link FrameworkFactory} APIs like a
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
 * the bundles in the boot layer also be exported by the system bundle. Setting
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
 * AtomosRuntime atomosRuntime = AtomosRuntime.newAtomosRuntime();
 * 
 * // Add a new layer using a Path that contains a set of modules to load
 * Path modulesDir = getModulesDir();
 * atomosRuntime.addLayer(List.of(atomosRuntime.getBootLayer()), "child", LoaderType.OSGI, modulesDir);
 *
 * // The Atomos runtime must be used to create the framework in order
 * // use the additional children layers added
 * Framework framework = atomosRuntime.newFramework(frameworkConfig);
 * framework.start();
 * </pre>
 * 
 * When using the AtomosRuntime to create a new Framework the {@link Constants#FRAMEWORK_SYSTEMPACKAGES}
 * is set to the empty value automatically.
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
public interface AtomosRuntime extends FrameworkFactory {
	/**
	 * The loader type used for the class loaders of an Atomos layer.
	 */
	public enum LoaderType {
		OSGI, SINGLE, MANY
	}

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
	AtomosBundleInfo getAtomosBundle(String bundleLocation);

	/**
	 * Returns the OSGi bundle installed which is associated with the specified
	 * Atomos Bundle.
	 * 
	 * @return the OSGi bundle or {@code null} if the Atomos bundle has not been
	 *         installed or if there is no OSGi Framework initialized with the
	 *         Atomos Runtime.
	 */
	Bundle getBundle(AtomosBundleInfo atomosBundle);

	/**
	 * Adds a layer with the specified parents and loads modules from the specified
	 * module paths
	 * 
	 * @param parents     the parents for the new layer
	 * @param name        the name of the new layer
	 * @param loaderType  the type of class loader to use
	 * @param modulePaths the paths to load modules for the new layer
	 * @return a newly created layer
	 * @throws UnsupportedOperationException if {@link #modulesSupported()} returns false.
	 */
	AtomosLayer addLayer(List<AtomosLayer> parents, String name, LoaderType loaderType, Path... modulePaths);

	/**
	 * A convenience method that adds the modules found at the specified path
	 * to a new child layer of the boot layer.
	 * @param name The name of the layer.
	 * @param path The path to the modules.  If {@code null} then the default will try to
	 * determine the location on disk of the atomos.framework module and look for a
	 * folder with the same name as the specified name of the layer.
	 * @throws UnsupportedOperationException if {@link #modulesSupported()} returns false.
	 */
	AtomosLayer addModules(String name, Path path);

	/**
	 * Returns {@code true} if modules and additional layers are supported.
	 * @return if modules and additional layers are supported.
	 */
	boolean modulesSupported();
	/**
	 * The initial boot Atomos layer
	 * 
	 * @return the boot Atomos layer
	 */
	AtomosLayer getBootLayer();

	/**
	 * Creates a new {@link Framework} with the specified framework configuration
	 * using this AtomosRuntime to provide the AtomosBundleInfo for installation
	 * into the new Framework.
	 * 
	 * @see FrameworkFactory#newFramework(Map)
	 * @param frameworkConfig the framework configuration
	 * @return A new, configured {@link Framework} instance. The framework instance
	 *         must be in the {@link Bundle#INSTALLED} state.
	 */
	@Override
	Framework newFramework(Map<String, String> frameworkConfig);

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
		AtomosRuntime atomosRuntime = newAtomosRuntime();
		if (atomosRuntime.modulesSupported()) {
			String modulesDirPath = frameworkConfig.get(ATOMOS_MODULES_DIR);
			Path modulesPath = modulesDirPath == null ? null : new File(modulesDirPath).toPath();
			atomosRuntime.addModules("modules", modulesPath);
		}

		Framework framework = atomosRuntime.newFramework(frameworkConfig);
		framework.start();
		return framework;
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
	 * Creates a new AtomosRuntime that can be used to create a new Atomos framework
	 * instance. If Atomos is running as a Java Module then this AtomosRuntime can
	 * be used to create additional layers by using the
	 * {@link #addLayer(List, String, LoaderType, Path...)} method. If the additional layers are added
	 * before {@link #newFramework(Map) creating} and {@link Framework#init()
	 * initializing} the framework then the Atomos bundles found in the added layers
	 * will be automatically installed and started according to the
	 * {@link #ATOMOS_BUNDLE_INSTALL} and {@link #ATOMOS_BUNDLE_START} options.
	 * <p>
	 * Note that this AtomosRuntime {@link #newFramework(Map)} must be used for a
	 * new {@link Framework framework} instance to use the layers added to this
	 * AtomosRuntime.
	 * 
	 * @return a new AtomosRuntime.
	 */
	static AtomosRuntime newAtomosRuntime() {
		return AtomosRuntimeBase.newAtomosRuntime();
	}
}
