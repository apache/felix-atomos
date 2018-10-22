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
import java.util.Collection;
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

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.eclipse.osgi.internal.framework.EquinoxContainer;
import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
import org.eclipse.osgi.storage.url.reference.ReferenceInputStream;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.FrameworkWiring;

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
public class AtomosRuntimeImpl implements AtomosRuntime, SynchronousBundleListener{
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
	final Map<String, AtomosBundleInfoImpl> byAtomosLocation = new HashMap<>();
	final Map<AtomosLayer, Collection<String>> byAtomosLayer = new HashMap<>();
	final Map<Configuration, AtomosLayerImpl> byConfig = new HashMap<>();
	final AtomosLayer bootLayer = createAtomLayer(thisConfig, "boot");
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
			AtomosLayer layer = atomosBundle.getAtomosLayer();
			byAtomosLayer.computeIfAbsent(layer, (l) -> new ArrayList<>()).add(osgiLocation);
		} finally {
			bundleLock.writeLock().unlock();
		}
	}

	private void removeFromInstalledBundles(Bundle bundle) {
		String location = bundle.getLocation();
		bundleLock.writeLock().lock();
		try {
			AtomosBundleInfo removed = byOSGiLocation.remove(location);
			byAtomosLayer.computeIfAbsent(removed.getAtomosLayer(), (l) -> new ArrayList<>()).remove(location);
		} finally {
			bundleLock.writeLock().unlock();
		}
	}

	AtomosBundleInfoImpl getByAtomLocation(String location) {
		bundleLock.readLock().lock();
		try {
			return byAtomosLocation.get(location);
		} finally {
			bundleLock.readLock().unlock();
		}
	}

	AtomosLayerImpl getByConfig(Configuration config) {
		bundleLock.readLock().lock();
		try {
			return byConfig.get(config);
		} finally {
			bundleLock.readLock().unlock();
		}
	}

	Collection<String> getInstelledLocations(AtomosLayer layer) {
		bundleLock.readLock().lock();
		try {
			return new ArrayList<>(byAtomosLayer.computeIfAbsent(layer, (l) -> new ArrayList<>()));
		} finally {
			bundleLock.readLock().unlock();
		}
	}

	@Override
	public AtomosBundleInfo getAtomBundle(String bundleLocation) {
		return getByOSGiLocation(bundleLocation);
	}

	@Override
	public AtomosLayer addLayer(Configuration layerConfig, String name) {
		if (bootLayer.getModuleLayer().isEmpty()) {
			throw new IllegalStateException("Cannot add module layers when Atomos is not loaded as module.");
		}
		AtomosLayer atomosLayer = createAtomLayer(layerConfig, name);
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

	BundleContext getBundleContext() {
		AtomosHookConfigurator current = configurator;
		if (current == null) {
			return null;
		}
		return configurator.bc;
	}

	ThreadLocal<AtomosBundleInfo> installingInfo = new ThreadLocal<>();
	Bundle installAtomosBundle(String prefix, AtomosBundleInfo atomosBundle) throws BundleException {
		if (AtomosHookConfigurator.DEBUG) {
			System.out.println("Installing atomos bundle: " + prefix + atomosBundle.getLocation()); //$NON-NLS-1$
		}

		if (configurator == null) {
			throw new IllegalStateException("No framework has been created.");
		}

		if (prefix.indexOf(':') != -1) {
			throw new IllegalArgumentException("The prefix cannot contain ':'");
		}

		BundleContext bc = configurator.getBundleContext();
		File emptyJar = configurator.getEmptyJar();
		if (bc == null || emptyJar == null) {
			throw new IllegalStateException("Framework has not been initialized.");
		}
		String location = atomosBundle.getLocation();
		if (!Constants.SYSTEM_BUNDLE_LOCATION.equals(location)) {
			location = prefix + ':' + atomosBundle.getLocation();
		}
		AtomosLayerImpl atomosLayer = (AtomosLayerImpl) atomosBundle.getAtomosLayer();
		if (!atomosLayer.isValid()) {
			throw new BundleException("Atomos layer has been uninstalled.", BundleException.INVALID_OPERATION);
		}

		addToInstalledBundles(location, (AtomosBundleInfoImpl) atomosBundle);
		installingInfo.set(atomosBundle);
		Bundle result = null;
		try {
			result = bc.installBundle(location, new ReferenceInputStream(emptyJar));
		} finally {
			// check if the layer is still valid
			if (!atomosLayer.isValid()) {
				// The atomosLayer became invalid while installing
				if (result != null) {
					result.uninstall();
					result = null;
				}
			}
			installingInfo.set(null);
		}
		return result;
	}

	AtomosBundleInfo currentlyInstalling() {
		return installingInfo.get();
	}

	AtomosLayerImpl createAtomLayer(Configuration config, String name) {
		AtomosLayerImpl existing = getByConfig(config);
		if (existing != null) {
			return existing;
		}
		bundleLock.writeLock().lock();
		try {
			AtomosLayerImpl result = new AtomosLayerImpl(config, name);
			addAtomLayer(result);
			return result;
		} finally {
			bundleLock.writeLock().unlock();
		}
	}


	@Override
	public void bundleChanged(BundleEvent event) {
		if (event.getType() == BundleEvent.UNINSTALLED) {
			removeFromInstalledBundles(event.getBundle());
		}
	}

	private void addAtomLayer(AtomosLayerImpl atomosLayer) {
		Configuration config = atomosLayer.getModuleLayer().map((m) -> m.configuration()).orElse(null);
		if (byConfig.putIfAbsent(config, atomosLayer) != null) {
			throw new IllegalStateException("AtomosLayer already exists for configuration.");
		}

		for (AtomosBundleInfo atomosBundle : atomosLayer.getAtomosBundles()) {
			if (byAtomosLocation.putIfAbsent(atomosBundle.getLocation(), (AtomosBundleInfoImpl) atomosBundle) != null) {
				throw new IllegalStateException("Atomos bundle location already exists: " + atomosBundle.getLocation());
			}
		}
		for (AtomosLayer parent : atomosLayer.getParents()) {
			((AtomosLayerImpl) parent).addChild(atomosLayer);
		}
	}

	class AtomosLayerImpl implements AtomosLayer {
		private final String name;
		private final Optional<ModuleLayer> moduleLayer;
		private final Set<AtomosBundleInfo> atomosBundles;
		private final List<AtomosLayer> parents;
		private final Set<AtomosLayer> children = new HashSet<>();
		private volatile boolean valid = true;

		AtomosLayerImpl(Configuration config, String name) {
			this.name = name == null ? "" : name;
			this.parents = findParents(config, this.name);
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

		void addChild(AtomosLayerImpl child) {
			children.add(child);
		}

		void removeChild(AtomosLayerImpl child) {
			children.remove(child);
		}

		private List<AtomosLayer> findParents(Configuration config, String name) {
			if (config == null || config.parents().isEmpty()) {
				return List.of();
			}
			List<AtomosLayer> found = new ArrayList<>(config.parents().size());
			for (Configuration parentConfig : config.parents()) {
				AtomosLayerImpl existingParent = getByConfig(parentConfig);
				if (existingParent != null) {
					found.add(existingParent);
				} else {
					found.add(createAtomLayer(parentConfig, name));
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
			Set<AtomosBundleInfo> bootBundles = findModuleLayerAtomosBundles(ModuleLayer.boot());
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
						Object content = getBundleContent(manifest);
						if (content != null) {
							String location;
							if (EquinoxContainer.NAME.equals(symbolicName)) {
								location = Constants.SYSTEM_BUNDLE_LOCATION;
							} else {
								location = content instanceof File ? ((File) content).getPath() : ((JarFile) content).getName();
								if (!getName().isEmpty()) {
									location = getName() + ":" + location;
								}
							}
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
			Set<AtomosBundleInfo> result = findModuleLayerAtomosBundles(moduleLayer.get());
			return Collections.unmodifiableSet(result);
		}

		private Set<AtomosBundleInfo> findModuleLayerAtomosBundles(ModuleLayer moduleLayer) {
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
				if (EquinoxContainer.NAME.equals(resolved.name())) {
					location = Constants.SYSTEM_BUNDLE_LOCATION;
				} else if (!getName().isEmpty()) {
					location = getName() + ":" + location;
				}
				// include only if it is not excluded
				Module m = descriptorMap.get(resolved.reference().descriptor());
				found.add(new AtomosBundleInfoImpl(AtomosRuntimeImpl.this, this, resolved, m, location, resolved.name(), version));
			}

			return found;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Set<AtomosLayer> getChildren() {
			bundleLock.readLock().lock();
			try {
				return new HashSet<>(children);
			} finally {
				bundleLock.readLock().unlock();
			}
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

		@Override
		public void uninstall() throws BundleException {
			List<Bundle> uninstalledBundles = new ArrayList<>();
			BundleContext bc = getBundleContext();
			if (bc != null) {
				uninstallLayer(uninstalledBundles, bc);
			}

			// now remove the layer hierarchy
			bundleLock.writeLock().lock();
			try {
				removeLayerFromHierarchy();
			} finally {
				bundleLock.writeLock().unlock();
			}

			if (bc != null) {
				// now refresh any uninstalled bundles
				bc.getBundle(Constants.SYSTEM_BUNDLE_LOCATION).adapt(FrameworkWiring.class).refreshBundles(uninstalledBundles);
			}
		}
		private void removeLayerFromHierarchy() {
			for (AtomosLayer parent : getParents()) {
				((AtomosLayerImpl) parent).removeChild(this);
			}
			for (AtomosLayer child : getChildren()) {
				((AtomosLayerImpl) child).removeLayerFromHierarchy();
			}
		}

		void uninstallLayer(List<Bundle> uninstalledBundles, BundleContext bc) throws BundleException {
			// mark as invalid first to prevent installs
			valid = false;
			if (getBootLayer().equals(this)) {
				throw new UnsupportedOperationException("Cannot uninstall the boot layer.");
			}
			// first uninstall all children
			for (AtomosLayer child : getChildren()) {
				((AtomosLayerImpl) child).uninstallLayer(uninstalledBundles, bc);
			}
			uninstallBundles(uninstalledBundles, bc);
		}

		boolean isValid() {
			return valid;
		}

		private void uninstallBundles(List<Bundle> uninstalled, BundleContext bc) throws BundleException {
			for (String installLoc : getInstelledLocations(this)) {
				Bundle b = bc.getBundle(installLoc);
				if (b != null) {
					uninstalled.add(b);
					b.uninstall();
				}
			}
		}
	}
}
