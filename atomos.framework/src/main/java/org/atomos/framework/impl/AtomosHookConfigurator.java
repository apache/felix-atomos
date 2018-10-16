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
package org.atomos.framework.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.internal.framework.EquinoxConfiguration;
import org.eclipse.osgi.internal.hookregistry.BundleFileWrapperFactoryHook;
import org.eclipse.osgi.internal.hookregistry.ClassLoaderHook;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.loader.BundleLoader;
import org.eclipse.osgi.internal.loader.EquinoxClassLoader;
import org.eclipse.osgi.internal.loader.ModuleClassLoader;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.eclipse.osgi.storage.bundlefile.BundleFileWrapper;
import org.eclipse.osgi.storage.bundlefile.MRUBundleFileList;
import org.eclipse.osgi.storage.url.reference.ReferenceInputStream;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

public class AtomosHookConfigurator implements HookConfigurator {
	public static final String DEBUG_ATOM_FWK = "equinox.atomos.framework/debug"; //$NON-NLS-1$
	public static boolean DEBUG = false;

	static final byte[] EMPTY_JAR;
	static {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			JarOutputStream jos = new JarOutputStream(baos);
			ZipEntry bootBundlePropsEntry = new ZipEntry("atomosbundle.properties"); //$NON-NLS-1$
			jos.putNextEntry(bootBundlePropsEntry);
			Properties bootBundleProps = new Properties();
			bootBundleProps.setProperty("atomosbundle", "true"); //$NON-NLS-1$ //$NON-NLS-2$
			bootBundleProps.store(jos, "AtomBundle"); //$NON-NLS-1$
			jos.close();
			EMPTY_JAR = baos.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static final ThreadLocal<AtomosRuntimeImpl> bootAtomRuntime = new ThreadLocal<>();

	private final AtomosRuntimeImpl atomosRuntime;
	volatile BundleContext bc;
	volatile File emptyJar;

	public AtomosHookConfigurator() {
		AtomosRuntimeImpl bootRuntime = bootAtomRuntime.get();
		atomosRuntime = bootRuntime == null ? new AtomosRuntimeImpl() : bootRuntime;
		atomosRuntime.setConfigurator(this);
	}
	@Override
	public void addHooks(HookRegistry hookRegistry) {
		DEBUG = hookRegistry.getContainer().getConfiguration().getDebugOptions().getBooleanOption(DEBUG_ATOM_FWK,
				false);
		if (atomosRuntime.getBootLayer().getAtomosBundles().isEmpty()) {
			// nothing for us to do here
			if (DEBUG) {
				Debug.println("No atomos bundles found.  Likely not running on the module path.");
			}
			return;
		}

		// add a class loading hook that returns a ModuleClassLoader that simply
		// for atomos bundles
		hookRegistry.addClassLoaderHook(new ClassLoaderHook() {
			@Override
			public ModuleClassLoader createClassLoader(ClassLoader parent, EquinoxConfiguration configuration,
					BundleLoader delegate, Generation generation) {
				AtomosBundleInfoImpl atomosBundle = atomosRuntime.getByOSGiLocation(generation.getBundleInfo().getLocation());
				if (atomosBundle != null) {
					return createAtomClassLoader(atomosBundle, parent, configuration, delegate, generation);
				}
				// return null to indicate the frameworks built-in class loader should be used.
				return null;
			}
		});

		final Debug debug = hookRegistry.getContainer().getConfiguration().getDebug();
		final MRUBundleFileList mruList = new MRUBundleFileList(0, debug);
		hookRegistry.addBundleFileWrapperFactoryHook(new BundleFileWrapperFactoryHook() {
			@Override
			public BundleFileWrapper wrapBundleFile(BundleFile bundleFile, Generation generation, boolean base) {
				String location = generation.getBundleInfo().getLocation();
				AtomosBundleInfoImpl atomosBundle = (AtomosBundleInfoImpl) atomosRuntime.getByOSGiLocation(location);
				if (atomosBundle != null) {
					try {
						return new AtomosBundleFileWrapper(atomosBundle, bundleFile, generation, mruList, debug);
					} catch (IOException e) {
						hookRegistry.getContainer().getEventPublisher().publishFrameworkEvent(FrameworkEvent.ERROR,
								generation.getRevision().getBundle(), e);
					}
				}
				return null;
			}
		});

		hookRegistry.addActivatorHookFactory(() -> new BundleActivator() {
			@Override
			public void start(BundleContext bc) throws BundleException, IOException {
				AtomicLong stateStamp = new AtomicLong(hookRegistry.getContainer().getStorage().getModuleDatabase().getRevisionsTimestamp());
				// TODO there has to be a better way to do this; perhaps we need a new hook in equinox
				// need to ensure the class loaders for atomos bundles are created eagerly to ensure
				// they are initialized.  Otherwise if they are delegated to directly before
				// lazy initialization they will fail to work.
				bc.addBundleListener(new SynchronousBundleListener() {
					@Override
					public void bundleChanged(BundleEvent event) {
						if (event.getType() == BundleEvent.RESOLVED) {
							// only do this work if the timestamp has changed indicating a new resolution state
							// many resolution events can be sent for a single resolve operation
							if (stateStamp.get() != hookRegistry.getContainer().getStorage().getModuleDatabase().getRevisionsTimestamp()) {
								for (Bundle b : bc.getBundles()) {
									if (atomosRuntime.getAtomBundle(b.getLocation()) != null) {
										BundleWiring wiring = b.adapt(BundleWiring.class);
										if (wiring != null) {
											wiring.getClassLoader();
										}
									}
								}
							}
						}
					}
				});
				AtomosHookConfigurator.this.bc = bc;
				emptyJar = bc.getDataFile("atomosEmptyBundle.jar");
				Files.write(emptyJar.toPath(), EMPTY_JAR);

				String initialBundleStartLevel = bc.getProperty(AtomosRuntime.ATOMOS_INITIAL_BUNDLE_START_LEVEL);
				if (initialBundleStartLevel != null) {
					// set the default initial bundle startlevel before installing the atomos bundles
					FrameworkStartLevel fwkStartLevel = bc.getBundle().adapt(FrameworkStartLevel.class);
					fwkStartLevel.setInitialBundleStartLevel(Integer.parseInt(initialBundleStartLevel));
				}
				boolean installBundles = Boolean.valueOf(hookRegistry.getConfiguration().getConfiguration(AtomosRuntime.ATOMOS_BUNDLE_INSTALL, "true")); //$NON-NLS-1$
				boolean startBundles = Boolean.valueOf(hookRegistry.getConfiguration().getConfiguration(AtomosRuntime.ATOMOS_BUNDLE_START, "true")); //$NON-NLS-1$
				installAtomBundles(atomosRuntime.getBootLayer(), installBundles, startBundles);
				bc.registerService(AtomosRuntime.class, atomosRuntime, null);
			}

			@Override
			public void stop(BundleContext bc) {

			}
		});

		hookRegistry.addStorageHookFactory(new AtomosStorageHookFactory(atomosRuntime, hookRegistry));
	}

	ModuleClassLoader createAtomClassLoader(AtomosBundleInfoImpl atomosBundle, ClassLoader parent,
			EquinoxConfiguration configuration, BundleLoader delegate, Generation generation) {
		Module m = atomosBundle.getModule().orElseThrow(() -> new IllegalStateException("No module found for bundle: " + atomosBundle.getLocation()));
		ClassLoader mLoader = m.getClassLoader();
		if (mLoader instanceof AtomosClassLoader) {
			((AtomosClassLoader) mLoader).init(configuration, delegate, generation);
			return (ModuleClassLoader) mLoader;
		}
		return new AtomDelegateLoader(parent, configuration, delegate, generation, m.getClassLoader());
	}

	void installAtomBundles(AtomosLayer atomosLayer, boolean installBundles, boolean startBundles) throws BundleException {
		if (installBundles) {
			List<Bundle> bundles = new ArrayList<>();
			for (AtomosBundleInfo atomosBundle : atomosLayer.getAtomosBundles()) {
				Bundle b = atomosBundle.install("atomos");
				if (b != null) {
					bundles.add(b);
				}
			}
			if (startBundles) {
				for (Bundle b : bundles) {
					BundleRevision rev = b.adapt(BundleRevision.class);
					if ((rev.getTypes() & BundleRevision.TYPE_FRAGMENT) == 0) {
						b.start();
					}
				}
				for (AtomosLayer child : atomosLayer.getChildren()) {
					installAtomBundles(child, installBundles, startBundles);
				}
			}
		}
	}

	Bundle installAtomBundle(String prefix, AtomosBundleInfo atomosBundle) throws BundleException {
		if (DEBUG) {
			System.out.println("Installing atomos bundle: " + prefix + atomosBundle.getLocation()); //$NON-NLS-1$
		}
		if (prefix.indexOf(':') != -1) {
			throw new IllegalArgumentException("The prefix cannot contain ':'");
		}
		if (bc == null) {
			throw new IllegalStateException("Framework has not been initialized.");
		}
		String location = prefix + ':' + atomosBundle.getLocation();
		atomosRuntime.addToInstalledBundles(location, (AtomosBundleInfoImpl) atomosBundle);
		try {
			return bc.installBundle(location, new ReferenceInputStream(emptyJar));
		} catch (BundleException e) {
			if (e.getType() == BundleException.DUPLICATE_BUNDLE_ERROR) {
				// Something changed in the location information for the atomos bundle.
				// uninstall the existing and try again.
				return overrideExistingInstall(location, atomosBundle);
			}
			throw e;
		}
	}

	private Bundle overrideExistingInstall(String location, AtomosBundleInfo bundleInfo) throws BundleException {
		Requirement req = new Requirement() {
			@Override
			public Resource getResource() {
				return null;
			}

			@Override
			public String getNamespace() {
				return IdentityNamespace.IDENTITY_NAMESPACE;
			}

			@Override
			public Map<String, String> getDirectives() {
				String identityFilter = "(" + IdentityNamespace.IDENTITY_NAMESPACE + "=" + bundleInfo.getSymbolicName()
						+ ")";
				return Collections.singletonMap(Namespace.REQUIREMENT_FILTER_DIRECTIVE, identityFilter);
			}

			@Override
			public Map<String, Object> getAttributes() {
				return Collections.emptyMap();
			}
		};
		Collection<BundleCapability> found = bc.getBundle().adapt(FrameworkWiring.class).findProviders(req);
		if (found.isEmpty()) {
			throw new IllegalStateException("No existing bundle found: " + bundleInfo.getSymbolicName());
		}
		for (BundleCapability cap : found) {
			cap.getRevision().getBundle().uninstall();
		}
		return bc.installBundle(location, new ByteArrayInputStream(EMPTY_JAR));
	}

	/**
	 * A bundle class loader that delegates to a module class loader. The purpose of
	 * this class loader is to simply delegate local class/resource lookups to the
	 * delegate class loader.
	 */
	static class AtomDelegateLoader extends EquinoxClassLoader {
		static {
			ClassLoader.registerAsParallelCapable();
		}
		private final ClassLoader delegateClassLoader;

		public AtomDelegateLoader(ClassLoader parent, EquinoxConfiguration configuration, BundleLoader delegate,
				Generation generation, ClassLoader delegateClassLoader) {
			super(parent, configuration, delegate, generation);
			this.delegateClassLoader = delegateClassLoader;
		}

		@Override
		public Class<?> findLocalClass(String classname) throws ClassNotFoundException {
			if (delegateClassLoader == null) {
				throw new ClassNotFoundException();
			}
			return delegateClassLoader.loadClass(classname);
		}

		@Override
		public URL findLocalResource(String resource) {
			if (delegateClassLoader == null) {
				return null;
			}
			return delegateClassLoader.getResource(resource);
		}

		@Override
		public Enumeration<URL> findLocalResources(String resource) {
			if (delegateClassLoader == null) {
				return Collections.enumeration(Collections.<URL>emptyList());
			}
			try {
				return delegateClassLoader.getResources(resource);
			} catch (IOException e) {
				return Collections.enumeration(Collections.<URL>emptyList());
			}
		}
	}
}
