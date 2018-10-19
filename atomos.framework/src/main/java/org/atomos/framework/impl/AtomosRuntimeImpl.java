package org.atomos.framework.impl;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ResolvedModule;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.eclipse.osgi.internal.framework.EquinoxContainer;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;

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
public class AtomosRuntimeImpl implements AtomosRuntime {
	static final String JAR_PROTOCOL = "jar"; //$NON-NLS-1$
	static final String FILE_PROTOCOL = "file"; //$NON-NLS-1$
	static final Module frameworkModule = HookConfigurator.class.getModule();
	static final Module thisModule = AtomosRuntimeImpl.class.getModule();
	static final Configuration thisConfig;
	static {
		ModuleLayer thisLayer = thisModule.getLayer();
		thisConfig = thisLayer != null ? thisLayer.configuration() : null;
	}

	volatile AtomosHookConfigurator configurator;
	final ReentrantReadWriteLock bundleLock = new ReentrantReadWriteLock();
	final Map<String, AtomosBundleInfoImpl> byOSGiLocation = new HashMap<>();
	final Map<String, AtomosBundleInfoImpl> byAtomLocation = new HashMap<>();
	final Map<Configuration, AtomLayerImpl> byConfig = new HashMap<>();
	final AtomosLayer bootLayer = createAtomLayer(thisConfig);
	final Function<String, ClassLoader> clFunction = (n) -> new AtomosClassLoader(null);

	AtomosBundleInfoImpl getByOSGiLocation(String location) {
		bundleLock.readLock().lock();
		try {
			return byOSGiLocation.get(location);
		} finally {
			bundleLock.readLock().unlock();
		}
	}

	void addToInstalledBundles(String osgiLocation, AtomosBundleInfoImpl atomosBundle) {
		bundleLock.writeLock().lock();
		try {
			byOSGiLocation.put(osgiLocation, atomosBundle);
		} finally {
			bundleLock.writeLock().unlock();
		}
	}

	AtomosBundleInfoImpl getByAtomLocation(String location) {
		bundleLock.readLock().lock();
		try {
			return byAtomLocation.get(location);
		} finally {
			bundleLock.readLock().unlock();
		}
	}

	AtomLayerImpl getByConfig(Configuration config) {
		bundleLock.readLock().lock();
		try {
			return byConfig.get(config);
		} finally {
			bundleLock.readLock().unlock();
		}
	}

	@Override
	public AtomosBundleInfo getAtomBundle(String bundleLocation) {
		return getByOSGiLocation(bundleLocation);
	}

	@Override
	public AtomosLayer addLayer(Configuration layerConfig) {
		if (bootLayer.getModuleLayer().isEmpty()) {
			throw new IllegalStateException("Cannot add module layers when Atomos is not loaded as module.");
		}
		AtomosLayer atomosLayer = createAtomLayer(layerConfig);
		return atomosLayer;
	}

	@Override
	public AtomosLayer getBootLayer() {
		return bootLayer;
	}

	@Override
	public Framework createFramework(Map<String, String> frameworkConfig) {
		frameworkConfig = frameworkConfig == null ? new HashMap<>() : new HashMap<>(frameworkConfig);
		if (frameworkConfig.get(Constants.FRAMEWORK_SYSTEMPACKAGES) == null) {
			// this is to prevent the framework from exporting all the packages provided
			// by the module path.
			frameworkConfig.put(Constants.FRAMEWORK_SYSTEMPACKAGES, "");
		}
		if (frameworkConfig.get("osgi.console") == null) {
			// Always allow the console to work
			frameworkConfig.put("osgi.console", "");
		}
		AtomosHookConfigurator.bootAtomRuntime.set(this);
		try {
			ServiceLoader<FrameworkFactory> loader;
			if (AtomosRuntime.class.getModule().getLayer() == null) {
				loader = ServiceLoader.load(FrameworkFactory.class, getClass().getClassLoader());
			} else {
				loader = ServiceLoader.load( //
						getClass().getModule().getLayer(), //
						FrameworkFactory.class);
			}
			FrameworkFactory factory = loader.findFirst() //
					.orElseThrow(() -> new RuntimeException("No Framework implementation found."));
			return factory.newFramework(frameworkConfig);
		} finally {
			AtomosHookConfigurator.bootAtomRuntime.set(null);
		}
	}

	void setConfigurator(AtomosHookConfigurator configurator) {
		this.configurator = configurator;
	}

	Bundle installAtomBundle(String prefix, AtomosBundleInfo atomosBundleInfo) throws BundleException {
		if (configurator == null) {
			throw new IllegalStateException("No framework has been created.");
		}
		return configurator.installAtomosBundle(prefix, atomosBundleInfo);
	}

	AtomLayerImpl createAtomLayer(Configuration config) {
		AtomLayerImpl existing = getByConfig(config);
		if (existing != null) {
			return existing;
		}
		bundleLock.writeLock().lock();
		try {
			AtomLayerImpl result = new AtomLayerImpl(config);
			addAtomLayer(result);
			return result;
		} finally {
			bundleLock.writeLock().unlock();
		}
	}

	private void addAtomLayer(AtomLayerImpl atomosLayer) {
		Configuration config = atomosLayer.getModuleLayer().map((m) -> m.configuration()).orElse(null);
		if (byConfig.putIfAbsent(config, atomosLayer) != null) {
			throw new IllegalStateException("AtomosLayer already exists for configuration.");
		}

		for (AtomosBundleInfo atomosBundle : atomosLayer.getAtomosBundles()) {
			if (byAtomLocation.putIfAbsent(atomosBundle.getLocation(), (AtomosBundleInfoImpl) atomosBundle) != null) {
				throw new IllegalStateException("Atomos bundle location already exists: " + atomosBundle.getLocation());
			}
		}
		for (AtomosLayer parent : atomosLayer.getParents()) {
			((AtomLayerImpl) parent).addChild(atomosLayer);
		}
	}

	class AtomLayerImpl implements AtomosLayer {
		private final Optional<ModuleLayer> moduleLayer;
		private final Set<AtomosBundleInfo> atomosBundles;
		private final List<AtomosLayer> parents;
		private final Set<AtomosLayer> children = new HashSet<>();
		AtomLayerImpl(Configuration config) {
			this.parents = findParents(config);
			this.moduleLayer = Optional.ofNullable(findModuleLayer(config));
			this.atomosBundles = findAtomosBundles();

		}

		private ModuleLayer findModuleLayer(Configuration config) {
			if (config == null) {
				return null;
			}
			if (config.equals(thisConfig)) {
				return thisModule.getLayer();
			}
			if (Configuration.empty().equals(config)) {
				return ModuleLayer.empty();
			}
			List<ModuleLayer> parentLayers = parents.stream().sequential().map((a) -> a.getModuleLayer().get()).collect(Collectors.toList());
			ModuleLayer.Controller controller = ModuleLayer.defineModules(config, parentLayers, clFunction);
			return controller.layer();
		}

		void addChild(AtomLayerImpl child) {
			children.add(child);
		}

		private List<AtomosLayer> findParents(Configuration config) {
			if (config == null || config.parents().isEmpty()) {
				return List.of();
			}
			List<AtomosLayer> found = new ArrayList<>(config.parents().size());
			for (Configuration parentConfig : config.parents()) {
				AtomLayerImpl existingParent = getByConfig(parentConfig);
				if (existingParent != null) {
					found.add(existingParent);
				} else {
					found.add(createAtomLayer(parentConfig));
				}
			}
			return Collections.unmodifiableList(found);
		}

		private Set<AtomosBundleInfo> findAtomosBundles() {
			if (moduleLayer.isEmpty()) {
				return findClassPathAtomosBundles();
			}
			return findModuleLayerAtomosBundles();
		}

		private Set<AtomosBundleInfo> findClassPathAtomosBundles() {
			// first get the boot modules
			Set<AtomosBundleInfo> bootBundles = findModuleLayerAtomosBundles(ModuleLayer.boot(), Set.of());
			try {
				ClassLoader cl = getClass().getClassLoader();
				Set<URL> parentManifests = new HashSet<>();
				if(cl.getParent() != null) {
					Enumeration<URL> eParentManifests = cl.getParent().getResources(JarFile.MANIFEST_NAME);
					while (eParentManifests.hasMoreElements()) {
						parentManifests.add(eParentManifests.nextElement());
					}
				}
				Enumeration<URL> classpathManifests = cl.getResources(JarFile.MANIFEST_NAME);
				while (classpathManifests.hasMoreElements()) {
					URL manifest = classpathManifests.nextElement();
					if (parentManifests.contains(manifest)) {
						// ignore parent manifests
						continue;
					}
					Map<String, String> headers = ManifestElement.parseBundleManifest(manifest.openStream(), null);
					String bsnHeader = headers.get(Constants.BUNDLE_SYMBOLICNAME);
					ManifestElement[] element = ManifestElement.parseHeader(Constants.BUNDLE_SYMBOLICNAME, bsnHeader);
					String symbolicName = element != null ? element[0].getValue() : null;
					if (symbolicName != null) {
						if (EquinoxContainer.NAME.equals(symbolicName)) {
							// skip the framework
							continue;
						}
						Object content = getBundleContent(manifest);
						if (content != null) {
							String location = content instanceof File ? ((File) content).getPath() : ((JarFile) content).getName();
							Version version = Version.parseVersion(headers.get(Constants.BUNDLE_VERSION));
							if (content instanceof File) {
								bootBundles.add(new AtomosBundleInfoImpl(AtomosRuntimeImpl.this, this, (File) content, location, symbolicName, version));
							} else {
								bootBundles.add(new AtomosBundleInfoImpl(AtomosRuntimeImpl.this, this, (JarFile) content, location, symbolicName, version));
							}
						}
					}
				}
			} catch (IOException | BundleException e) {
				throw new IllegalStateException("Error finding class path bundles.", e);
			}
			return bootBundles;
		}
		/**
		 * Returns the bundle content that contains the specified manifest URL.
		 * The return type will be a JarFile or a File for an exploded bundle.
		 * @param manifest the manifest URL to get the bundle content for
		 * @return a JarFile or File
		 */
		private Object getBundleContent(URL manifest) {
			if (JAR_PROTOCOL.equals(manifest.getProtocol())) {
				// Use a connection to get the JarFile this avoids having to parse the jar: URL
				// For spring loader they support nested jars with additional !/
				// For example: 
				//   jar:file:/path/to/out.jar!/path/to/inner.jar!/META-INF/MANIFEST.MF
				// Instead of dealing with that just get the JarFile directly that supports this
				// embedded jar stuff
				try {
					URLConnection conn = manifest.openConnection();
					if (conn instanceof JarURLConnection) {
						return ((JarURLConnection) conn).getJarFile();
					}
				} catch (IOException e) {
					// TODO log?
				}
				// TODO either log or add tracing to help debug issues
			} else if (FILE_PROTOCOL.equals(manifest.getProtocol())) {
				try {
					File f = new File(manifest.toURI());
					// return two parents up from the manifest file
					return f.getParentFile().getParentFile();
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
			}
			return null;
		}
		private Set<AtomosBundleInfo> findModuleLayerAtomosBundles() {
			Set<AtomosBundleInfo> result = findModuleLayerAtomosBundles(moduleLayer.get(), Set.of(frameworkModule.getDescriptor()));
			return Collections.unmodifiableSet(result);
		}

		private Set<AtomosBundleInfo> findModuleLayerAtomosBundles(ModuleLayer moduleLayer, Set<ModuleDescriptor> exclude) {
			Map<ModuleDescriptor, Module> descriptorMap = moduleLayer.modules().stream()
					.collect(Collectors.toMap(Module::getDescriptor, m -> (m)));
			Set<AtomosBundleInfo> found = new LinkedHashSet<>();
			for (ResolvedModule resolved : moduleLayer.configuration().modules()) {
				String location = resolved.reference().location().get().toString();
				Version version = resolved.reference().descriptor().version().map((v) -> {
					try {
						return Version.valueOf(v.toString());
					} catch (IllegalArgumentException e) {
						return Version.emptyVersion;
					}
				}).orElse(Version.emptyVersion);
				
				if (!exclude.contains(resolved.reference().descriptor())) {
					// include only if it is not excluded
					Module m = descriptorMap.get(resolved.reference().descriptor());
					found.add(new AtomosBundleInfoImpl(AtomosRuntimeImpl.this, this, resolved, m, location, resolved.name(), version));
				}
			}
			return found;
		}

		@Override
		public Set<AtomosLayer> getChildren() {
			return Collections.unmodifiableSet(children);
		}

		@Override
		public List<AtomosLayer> getParents() {
			return parents;
		}

		@Override
		public Set<AtomosBundleInfo> getAtomosBundles() {
			return atomosBundles;
		}

		@Override
		public Optional<ModuleLayer> getModuleLayer() {
			return moduleLayer;
		}
	}
}
