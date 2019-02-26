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
package org.atomos.framework.modules;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.atomos.framework.base.AtomosClassLoader;
import org.atomos.framework.base.AtomosRuntimeBase;
import org.atomos.framework.base.AtomosRuntimeBase.AtomosLayerBase.AtomosBundleInfoBase;
import org.atomos.framework.base.JavaServiceNamespace;
import org.eclipse.osgi.container.ModuleRevisionBuilder;
import org.eclipse.osgi.container.ModuleRevisionBuilder.GenericInfo;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.internal.framework.EquinoxContainer;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.eclipse.osgi.storage.bundlefile.MRUBundleFileList;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.resource.Namespace;

public class AtomosRuntimeModules extends AtomosRuntimeBase {
	static final String OSGI_CONTRACT_NAMESPACE = "osgi.contract";
	static final String OSGI_VERSION_ATTR = "version:Version";
	static final Module thisModule = AtomosRuntimeModules.class.getModule();
	static final Configuration thisConfig = thisModule.getLayer() == null ? null : thisModule.getLayer().configuration();
	static final Function<String, ClassLoader> clFunction = (n) -> new AtomosClassLoader(null);

	final Map<Configuration, AtomosLayerBase> byConfig = new HashMap<>();
	private final AtomosLayer bootLayer = createBootLayer();

	@Override
	protected AtomosLayer createBootLayer() {
		return createAtomosLayer(thisConfig, "boot", -1, LoaderType.SINGLE);
	}
	

	AtomosLayerBase getByConfig(Configuration config) {
		lockRead();
		try {
			return byConfig.get(config);
		} finally {
			unlockRead();
		}
	}

	@Override
	protected AtomosLayer addLayer(List<AtomosLayer> parents, String name, long id, LoaderType loaderType, Path... paths) {
		if (bootLayer.adapt(ModuleLayer.class).isEmpty()) {
			throw new UnsupportedOperationException("Cannot add module layers when Atomos is not loaded as module.");
		}
		List<Configuration> parentConfigs = parents.stream().map((l) -> l.adapt(ModuleLayer.class).get().configuration()).collect(Collectors.toList());
		ModuleFinder finder = ModuleFinder.of(paths);
		List<String> roots = finder.findAll().stream().map((m) -> m.descriptor().name()).collect(Collectors.toList());
		Configuration config = Configuration.resolve(ModuleFinder.of(), parentConfigs, ModuleFinder.of(paths), roots);
		return createAtomosLayer(config, name, id, loaderType, paths);
	}

	@Override
	protected FrameworkFactory findFrameworkFactory() {
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
		return factory;
	}



	AtomosLayerBase createAtomosLayer(Configuration config, String name, long id, LoaderType loaderType, Path... paths) {
		AtomosLayerBase existing = getByConfig(config);
		if (existing != null) {
			return existing;
		}
		existing = getById(id);
		if (existing != null) {
			throw new IllegalArgumentException("The a layer already exists with the id: " + id + " " + existing.getName());
		}
		lockWrite();
		try {
			List<AtomosLayer> parents = findParents(config, name);
			id = id < 0 ? nextLayerId.getAndIncrement() : id;
			if (Configuration.empty().equals(config)) {
				name = "empty";
			}
			AtomosLayerModules result = new AtomosLayerModules(config, parents, id, name, loaderType, paths);
			addAtomosLayer(result);
			return result;
		} finally {
			unlockWrite();
		}
	}

	private List<AtomosLayer> findParents(Configuration config, String name) {
		if (config == null || config.parents().isEmpty()) {
			return List.of();
		}
		List<AtomosLayer> found = new ArrayList<>(config.parents().size());
		for (Configuration parentConfig : config.parents()) {
			AtomosLayerBase existingParent = getByConfig(parentConfig);
			if (existingParent != null) {
				found.add(existingParent);
			} else {
				// If it didn't exist already we really don't know what type of loader it is;
				// just use SINGLE for now
				// We also don't know what paths it could be using
				found.add(createAtomosLayer(parentConfig, name, -1, LoaderType.SINGLE));
			}
		}
		return Collections.unmodifiableList(found);
	}

	@Override
	protected void addingLayer(AtomosLayerBase atomosLayer) {
		Configuration config = atomosLayer.adapt(ModuleLayer.class).map((m) -> m.configuration()).orElse(null);
		if (byConfig.putIfAbsent(config, atomosLayer) != null) {
			throw new IllegalStateException("AtomosLayer already exists for configuration.");
		}
	}

	@Override
	protected void removedLayer(AtomosLayerBase atomosLayer) {
		byConfig.remove(atomosLayer.adapt(ModuleLayer.class).map((l) -> l.configuration()).orElse(null));
	}

	ModuleLayer findModuleLayer(Configuration config, List<AtomosLayer> parents, LoaderType loaderType) {
		if (config == null) {
			return null;
		}
		if (config.equals(thisConfig)) {
			return thisModule.getLayer();
		}
		if (Configuration.empty().equals(config)) {
			return ModuleLayer.empty();
		}
		List<ModuleLayer> parentLayers = parents.stream().sequential().map((a) -> a.adapt(ModuleLayer.class).get()).collect(Collectors.toList());
		ModuleLayer.Controller controller;
		switch (loaderType) {
		case OSGI:
			controller = ModuleLayer.defineModules(config, parentLayers, clFunction);
			break;
		case SINGLE:
			controller = ModuleLayer.defineModulesWithOneLoader(config, parentLayers, null);
			break;
		case MANY:
			controller = ModuleLayer.defineModulesWithManyLoaders(config, parentLayers, null);
			break;
		default:
			throw new UnsupportedOperationException(loaderType.toString());
		}
		return controller.layer();
	}


	@Override
	public AtomosLayer addModules(String name, Path path) {
		if (modulesSupported()) {
			if (path == null) {
				ResolvedModule resolved = thisConfig.findModule(AtomosRuntime.class.getModule().getName()).get();
				URI location = resolved.reference().location().get();
				if (location.getScheme().equals("file")) {
					// Use the module location as the relative base to locate the modules folder
					File thisModuleFile = new File(location);
					File candidate = new File(thisModuleFile.getParent(), "modules");
					path = candidate.isDirectory() ? candidate.toPath() : null;
				}
			}
			if (path != null) {
				return addLayer(Collections.singletonList(getBootLayer()), "modules", LoaderType.OSGI, path);
			}
		}
		return null;
	}

	@Override
	public boolean modulesSupported() {
		return thisConfig != null;
	}

	@Override
	public AtomosLayer getBootLayer() {
		return bootLayer;
	}

	@Override
	protected String getBundleLocation(Class<?> classFromBundle) {
		Module m = classFromBundle.getModule();
		if (m != null && m.isNamed()) {
			lockRead();
			try {
				return byAtomosKey.get(m);
			} finally {
				unlockRead();
			}
		}
		return super.getBundleLocation(classFromBundle);
	}

	@Override
	protected ClassLoader getClassLoader(AtomosBundleInfoBase atomosBundle) {
		return atomosBundle.adapt(Module.class).map((m) -> m.getClassLoader()).orElse(HookRegistry.class.getClassLoader());
	}

	@Override
	protected ModuleRevisionBuilder createBuilder(org.atomos.framework.base.AtomosRuntimeBase.AtomosLayerBase.AtomosBundleInfoBase atomosBundle, ModuleRevisionBuilder original,
			HookRegistry hookRegistry) {

		Optional<Module> module = atomosBundle.adapt(Module.class);
		if (module.isEmpty()) {
			return null;
		}

		List<GenericInfo> origCaps = original.getCapabilities();
		List<GenericInfo> origReqs = original.getRequirements();

		ModuleDescriptor desc = atomosBundle.adapt(Module.class).get().getDescriptor();
		ModuleRevisionBuilder builder = new ModuleRevisionBuilder();

		if (original.getSymbolicName() != null) {
			builder.setSymbolicName(original.getSymbolicName());
			builder.setVersion(original.getVersion());
			builder.setTypes(original.getTypes());
		} else {
			builder.setSymbolicName(desc.name());
			Version version = desc.version().map((v) -> {
				try {
					return Version.valueOf(v.toString());
				} catch (IllegalArgumentException e) {
					return Version.emptyVersion;
				}
			}).orElse(Version.emptyVersion);
			builder.setVersion(version);

			// add bundle and identity capabilities, do not create host capability for JPMS
			builder.addCapability(BundleNamespace.BUNDLE_NAMESPACE, Map.of(), Map.of(BundleNamespace.BUNDLE_NAMESPACE,
					desc.name(), BundleNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE, version));
			builder.addCapability(IdentityNamespace.IDENTITY_NAMESPACE, Map.of(),
					Map.of(IdentityNamespace.IDENTITY_NAMESPACE, desc.name(),
							IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE, version));

			// only do exports for non bundle modules
			// real OSGi bundles already have good export capabilities
			for (Exports exports : desc.exports()) {
				// TODO map targets to x-friends directive.
				builder.addCapability(PackageNamespace.PACKAGE_NAMESPACE, Map.of(),
						Map.of(PackageNamespace.PACKAGE_NAMESPACE, exports.source(), PackageNamespace.CAPABILITY_VERSION_ATTRIBUTE, Version.emptyVersion));
			}

			// add a contract based on the module name
			addOSGiContractCapability(builder, desc);

			// only do requires for non bundle modules
			// map requires to require bundle
			for (Requires requires : desc.requires()) {
				Map<String, String> directives = new HashMap<>();

				// determine the resolution value based on the STATIC modifier
				String resolution = requires.modifiers().contains(Requires.Modifier.STATIC) ? Namespace.RESOLUTION_OPTIONAL
						: Namespace.RESOLUTION_MANDATORY;
				directives.put(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, resolution);
				// determine the visibility value based on the TRANSITIVE modifier
				String visibility = requires.modifiers().contains(Requires.Modifier.TRANSITIVE)
						? BundleNamespace.VISIBILITY_REEXPORT
						: BundleNamespace.VISIBILITY_PRIVATE;
				directives.put(BundleNamespace.REQUIREMENT_VISIBILITY_DIRECTIVE, visibility);
				// create a bundle filter based on the requires name
				directives.put(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
						"(" + BundleNamespace.BUNDLE_NAMESPACE + "=" + requires.name() + ")");

				builder.addRequirement(BundleNamespace.BUNDLE_NAMESPACE, directives, Collections.emptyMap());
			}
		}

		// map provides to a made up namespace only to give proper resolution errors
		// (although JPMS will likely complain first
		for (Provides provides : desc.provides()) {
			builder.addCapability(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE, Map.of(),
					Map.of(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE, provides.service(),
							JavaServiceNamespace.CAPABILITY_PROVIDES_WITH, provides.providers()));
		}

		// map uses to a made up namespace only to give proper resolution errors
		// (although JPMS will likely complain first)
		for (String uses : desc.uses()) {
			builder.addRequirement(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE,
					Map.of(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL,
							Namespace.REQUIREMENT_FILTER_DIRECTIVE,
							"(" + JavaServiceNamespace.JAVA_SERVICE_NAMESPACE + "=" + uses + ")"),
					Map.of(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE, uses));
		}

		boolean bnsMatchesModuleName = builder.getSymbolicName().equals(desc.name());
		// add back the original
		origCaps.forEach((c) -> {
			Map<String, String> directives = c.getDirectives();
			Map<String, Object> attributes = c.getAttributes();
			if (BundleNamespace.BUNDLE_NAMESPACE.equals(c.getNamespace()) && !bnsMatchesModuleName) {
				attributes = new HashMap<>(attributes);
				Object name = attributes.get(BundleNamespace.BUNDLE_NAMESPACE);
				List<String> names = new ArrayList<>();
				if (name instanceof Collection) {
					@SuppressWarnings("unchecked")
					Collection<String> aliases = (Collection<String>) name;
					names.addAll(aliases);
				} else {
					names.add((String) name);
				}
				if (!names.contains(desc.name())) {
					names.add(desc.name());
				}
				attributes.put(BundleNamespace.BUNDLE_NAMESPACE, names);
			}
			builder.addCapability(c.getNamespace(), directives, attributes);
		});
		origReqs.forEach((r) -> builder.addRequirement(r.getNamespace(), r.getDirectives(), r.getAttributes()));

		return builder;
	}

	private void addOSGiContractCapability(ModuleRevisionBuilder builder, ModuleDescriptor desc) {
		Map<String, Object> attributes = Map.of(OSGI_CONTRACT_NAMESPACE, desc.name(), OSGI_VERSION_ATTR, builder.getVersion());
		Map<String, String> directives = Map.of();
		String uses = null;
		Set<Exports> exports = desc.exports();
		if (!exports.isEmpty()) {
			uses = exports.stream().map(e -> e.source()).sorted().collect(Collectors.joining(","));
			directives = Map.of(PackageNamespace.CAPABILITY_USES_DIRECTIVE, uses);
		}
		builder.addCapability(OSGI_CONTRACT_NAMESPACE, directives, attributes);
	}

	@Override
	protected void filterBasedOnReadEdges(AtomosBundleInfo atomosBundle, Collection<BundleCapability> candidates) {
		Module m = atomosBundle.adapt(Module.class).orElse(null);
		if (m == null) {
			filterNotVisible(atomosBundle, candidates);
		} else {
			for (Iterator<BundleCapability> iCands = candidates.iterator(); iCands.hasNext();) {
				BundleCapability candidate = iCands.next();
				AtomosBundleInfo candidateAtomos = getByOSGiLocation(candidate.getRevision().getBundle().getLocation());
				if (candidateAtomos == null || candidateAtomos.adapt(Module.class).isEmpty()) {
					iCands.remove();
				} else {
					if (!m.canRead(candidateAtomos.adapt(Module.class).get())) {
						iCands.remove();
					}
				}
			}
		}
	}

	public class AtomosLayerModules extends AtomosLayerBase {
		private final ModuleLayer moduleLayer;
		private final Set<AtomosBundleInfoBase> atomosBundles;
		AtomosLayerModules(Configuration config, List<AtomosLayer> parents, long id, String name, LoaderType loaderType, Path... paths) {
			super(parents, id, name, loaderType, paths);
			moduleLayer = findModuleLayer(config, parents, loaderType);
			atomosBundles = findAtomosBundles();
		}

		private Set<AtomosBundleInfoBase> findAtomosBundles() {
			return moduleLayer == null ? findClassPathAtomosBundles() : findModuleLayerAtomosBundles(moduleLayer);
		}

		private Set<AtomosBundleInfoBase> findModuleLayerAtomosBundles(ModuleLayer searchLayer) {
			Map<ModuleDescriptor, Module> descriptorMap = searchLayer.modules().stream()
					.collect(Collectors.toMap(Module::getDescriptor, m -> (m)));
			Set<AtomosBundleInfoBase> found = new LinkedHashSet<>();
			for (ResolvedModule resolved : searchLayer.configuration().modules()) {
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
				found.add(new AtomosBundleInfoModule(AtomosRuntimeModules.this, this, resolved, m, location, resolved.name(), version));
			}

			return Collections.unmodifiableSet(found);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> Optional<T> adapt(Class<T> type) {
			if (ModuleLayer.class.equals(type)) {
				return Optional.ofNullable((T) moduleLayer);
			}
			return super.adapt(type);
		}

		public class AtomosBundleInfoModule extends AtomosBundleInfoBase {
			/**
			 * The resolved module for this atomos bundle
			 */
			private final ResolvedModule resolvedModule;

			/**
			 * The module for this atomos bundle.
			 */
			private final Module module;

			public AtomosBundleInfoModule(AtomosRuntimeBase runtime, AtomosLayer atomosLayer, ResolvedModule resolvedModule, Module module, String location, String symbolicName, Version version) {
				super(location, symbolicName, version);
				this.resolvedModule = resolvedModule;
				this.module = module;
			}

			@Override
			protected final BundleFile getBundleFile(BundleFile bundleFile, Generation generation, MRUBundleFileList mruList, Debug debug) throws IOException {
				return new ModuleReaderBundleFile(resolvedModule.reference(), bundleFile.getBaseFile(), generation, mruList, debug);
			}

			@Override
			protected final Object getKey() {
				return module;
			}

			@SuppressWarnings("unchecked")
			@Override
			public <T> Optional<T> adapt(Class<T> type) {
				if (Module.class.equals(type)) {
					return Optional.ofNullable((T) module);
				}
				return super.adapt(type);
			}

			@Override
			protected URL getContentURL() {
				throw new UnsupportedOperationException("No content URL for Atomos module bundle.");
			}
		}

		@Override
		public Set<AtomosBundleInfo> getAtomosBundles() {
			return asSet(atomosBundles);
		}

		@Override
		protected void findBootLayerAtomosBundles(Set<AtomosBundleInfoBase> result) {
			result.addAll(findModuleLayerAtomosBundles(ModuleLayer.boot()));
		}
	}
}
