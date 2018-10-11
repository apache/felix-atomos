package org.atomos.framework.impl;

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ResolvedModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.osgi.internal.hookregistry.HookConfigurator;
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
			FrameworkFactory factory = ServiceLoader.load(thisModule.getLayer(), FrameworkFactory.class).findFirst().get();
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
		return configurator.installAtomBundle(prefix, atomosBundleInfo);
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
		if (byConfig.putIfAbsent(atomosLayer.getConfiguration(), atomosLayer) != null) {
			throw new IllegalStateException("AtomosLayer already exists for configuration.");
		}

		for (AtomosBundleInfo atomosBundle : atomosLayer.getAtomosBundles()) {
			if (byAtomLocation.putIfAbsent(atomosBundle.getLocation(), (AtomosBundleInfoImpl) atomosBundle) != null) {
				throw new IllegalStateException("Atom bundle location already exists: " + atomosBundle.getLocation());
			}
		}
		for (AtomosLayer parent : atomosLayer.getParents()) {
			((AtomLayerImpl) parent).addChild(atomosLayer);
		}
	}

	class AtomLayerImpl implements AtomosLayer {
		private final Configuration config;
		private final ModuleLayer moduleLayer;
		private final Set<AtomosBundleInfo> atomosBundles;
		private final List<AtomosLayer> parents;
		private final Set<AtomosLayer> children = new HashSet<>();
		AtomLayerImpl(Configuration config) {
			this.config = config;
			this.parents = findParents();
			this.moduleLayer = findModuleLayer();
			this.atomosBundles = findAtomBundles();

		}

		private ModuleLayer findModuleLayer() {
			if (config.equals(thisConfig)) {
				return thisModule.getLayer();
			}
			if (Configuration.empty().equals(config)) {
				return ModuleLayer.empty();
			}
			List<ModuleLayer> parentLayers = parents.stream().sequential().map((a) -> a.getModuleLayer()).collect(Collectors.toList());
			ModuleLayer.Controller controller = ModuleLayer.defineModules(config, parentLayers, clFunction);
			return controller.layer();
		}

		void addChild(AtomLayerImpl child) {
			children.add(child);
		}

		private List<AtomosLayer> findParents() {
			if (config.parents().isEmpty()) {
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

		private Set<AtomosBundleInfo> findAtomBundles() {
			Map<ModuleDescriptor, Module> descriptorMap = moduleLayer.modules().stream()
					.collect(Collectors.toMap(Module::getDescriptor, m -> (m)));
			Set<AtomosBundleInfo> found = new LinkedHashSet<>();
			for (ResolvedModule resolved : config.modules()) {
				String location = resolved.reference().location().get().toString();
				Version version = resolved.reference().descriptor().version().map((v) -> {
					try {
						return Version.valueOf(v.toString());
					} catch (IllegalArgumentException e) {
						return Version.emptyVersion;
					}
				}).orElse(Version.emptyVersion);
				
				if (!frameworkModule.getDescriptor().equals(resolved.reference().descriptor())) {
					// include only if it is not the framework module
					Module m = descriptorMap.get(resolved.reference().descriptor());
					found.add(new AtomosBundleInfoImpl(AtomosRuntimeImpl.this, this, resolved, m, location, resolved.name(), version));
				}
			}
			return Collections.unmodifiableSet(found);
		}
		@Override
		public Configuration getConfiguration() {
			return config;
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
		public ModuleLayer getModuleLayer() {
			return moduleLayer;
		}
	}
}
