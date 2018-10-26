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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.eclipse.osgi.framework.log.FrameworkLogEntry;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.internal.framework.EquinoxConfiguration;
import org.eclipse.osgi.internal.hookregistry.BundleFileWrapperFactoryHook;
import org.eclipse.osgi.internal.hookregistry.ClassLoaderHook;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.loader.BundleLoader;
import org.eclipse.osgi.internal.loader.EquinoxClassLoader;
import org.eclipse.osgi.internal.loader.ModuleClassLoader;
import org.eclipse.osgi.internal.log.EquinoxLogServices;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.eclipse.osgi.storage.bundlefile.BundleFileWrapper;
import org.eclipse.osgi.storage.bundlefile.MRUBundleFileList;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;

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
	volatile EquinoxLogServices logServices;

	public AtomosHookConfigurator() {
		AtomosRuntimeImpl bootRuntime = bootAtomRuntime.get();
		atomosRuntime = bootRuntime == null ? new AtomosRuntimeImpl() : bootRuntime;
		atomosRuntime.setConfigurator(this);
	}
	@Override
	public void addHooks(HookRegistry hookRegistry) {
		DEBUG = hookRegistry.getContainer().getConfiguration().getDebugOptions().getBooleanOption(DEBUG_ATOM_FWK,
				false);
		logServices = hookRegistry.getContainer().getLogServices();
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
					return createAtomosClassLoader(atomosBundle, parent, configuration, delegate, generation);
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
				AtomosHookConfigurator.this.bc = bc;
				AtomosFrameworkHooks hooks = new AtomosFrameworkHooks(atomosRuntime);
				bc.registerService(ResolverHookFactory.class, hooks, null);
				bc.registerService(CollisionHook.class, hooks, null);

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
				installAtomosBundles(atomosRuntime.getBootLayer(), installBundles, startBundles);
				bc.registerService(AtomosRuntime.class, atomosRuntime, null);
				new AtomosCommands(atomosRuntime).register(bc);
			}

			@Override
			public void stop(BundleContext bc) {

			}
		});

		hookRegistry.addStorageHookFactory(new AtomosStorageHookFactory(atomosRuntime, hookRegistry));
	}

	ModuleClassLoader createAtomosClassLoader(AtomosBundleInfoImpl atomosBundle, ClassLoader parent,
			EquinoxConfiguration configuration, BundleLoader delegate, Generation generation) {
		ClassLoader delegateTo;
		if (atomosBundle.getModule().isPresent()) {
			delegateTo = atomosBundle.getModule().get().getClassLoader();
			if (delegateTo instanceof AtomosClassLoader) {
				((AtomosClassLoader) delegateTo).init(configuration, delegate, generation);
				return (ModuleClassLoader) delegateTo;
			}			
		} else {
			delegateTo = HookRegistry.class.getClassLoader();
		}
		return new AtomosDelegateLoader(parent, configuration, delegate, generation, delegateTo);
	}

	void installAtomosBundles(AtomosLayer atomosLayer, boolean installBundles, boolean startBundles) throws BundleException {
		if (installBundles) {
			List<Bundle> bundles = new ArrayList<>();
			for (AtomosBundleInfo atomosBundle : atomosLayer.getAtomosBundles()) {
				try {
					Bundle b = atomosBundle.install("atomos");
					if (b != null && b.getBundleId() != 0) {
						bundles.add(b);
					}
				} catch (BundleException e) {
					logServices.log(AtomosRuntimeImpl.thisModule.getName(), FrameworkLogEntry.ERROR, "Error installing Atomos bundle.", e);
				}
			}
			if (startBundles) {
				for (Bundle b : bundles) {
					BundleRevision rev = b.adapt(BundleRevision.class);
					if ((rev.getTypes() & BundleRevision.TYPE_FRAGMENT) == 0) {
						try {
							b.start();
						} catch (BundleException e) {
							logServices.log(AtomosRuntimeImpl.thisModule.getName(), FrameworkLogEntry.ERROR, "Error starting Atomos bundle.", e);

						}
					}
				}
				for (AtomosLayer child : atomosLayer.getChildren()) {
					installAtomosBundles(child, installBundles, startBundles);
				}
			}
		}
	}

	public BundleContext getBundleContext() {
		return bc;
	}
	public File getEmptyJar() {
		return emptyJar;
	}
	/**
	 * A bundle class loader that delegates to a module class loader. The purpose of
	 * this class loader is to simply delegate local class/resource lookups to the
	 * delegate class loader.
	 */
	static class AtomosDelegateLoader extends EquinoxClassLoader {
		static {
			ClassLoader.registerAsParallelCapable();
		}
		private final ClassLoader delegateClassLoader;

		public AtomosDelegateLoader(ClassLoader parent, EquinoxConfiguration configuration, BundleLoader delegate,
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
