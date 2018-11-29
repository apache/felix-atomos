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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime.LoaderType;
import org.atomos.framework.impl.AtomosRuntimeImpl.AtomosLayerImpl;
import org.eclipse.osgi.container.ModuleContainerAdaptor.ModuleEvent;
import org.eclipse.osgi.container.ModuleRevisionBuilder;
import org.eclipse.osgi.container.ModuleRevisionBuilder.GenericInfo;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.hookregistry.StorageHookFactory;
import org.eclipse.osgi.internal.hookregistry.StorageHookFactory.StorageHook;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Namespace;

public class AtomosStorageHookFactory extends StorageHookFactory<AtomicBoolean, AtomicBoolean, StorageHook<AtomicBoolean, AtomicBoolean>> {
	static final String OSGI_CONTRACT_NAMESPACE = "osgi.contract";
	static final String OSGI_VERSION_ATTR = "version:Version";
	private final AtomosRuntimeImpl atomosRuntime;
	private final HookRegistry hookRegistry;
	
	public AtomosStorageHookFactory(AtomosRuntimeImpl atomosRuntime, HookRegistry hookRegistry) {
		this.atomosRuntime = atomosRuntime;
		this.hookRegistry = hookRegistry;
	}

	@Override
	public int getStorageVersion() {
		return 1;
	}

	@Override
	public AtomicBoolean createLoadContext(int version) {
		return new AtomicBoolean(false);
	}

	@Override
	public AtomicBoolean createSaveContext() {
		return new AtomicBoolean(false);
	}

	@Override
	protected StorageHook<AtomicBoolean, AtomicBoolean> createStorageHook(Generation generation) {
		return new StorageHook<AtomicBoolean, AtomicBoolean>(generation, this.getClass()) {

			@Override
			public void initialize(Dictionary<String, String> manifest) throws BundleException {
				// nothing
			}

			@Override
			public void load(AtomicBoolean loadContext, DataInputStream is) throws IOException {
				if (loadContext.compareAndSet(false, true)) {
					loadLayers(is);
				}
				if (is.readBoolean()) {
					String atomosLocation = is.readUTF();
					AtomosBundleInfoImpl atomosBundle = atomosRuntime.getByAtomLocation(atomosLocation);
					if (atomosBundle != null) {
						String osgiLocation = generation.getBundleInfo().getLocation();
						int firstColon = osgiLocation.indexOf(':');
						if (firstColon >= 0) {
							if (atomosLocation.equals(osgiLocation.substring(firstColon + 1))) {
								atomosRuntime.addToInstalledBundles(osgiLocation, atomosBundle);
								return;
							}
						}
					}
					// We throw an IllegalArgumentException to force a clean start.
					// NOTE this is really depends on an internal of the framework.
					throw new IllegalArgumentException();
				}
			}

			@Override
			public void save(AtomicBoolean saveContext, DataOutputStream os) throws IOException {
				if (saveContext.compareAndSet(false, true)) {
					saveLayers(os);
				}
				String location = generation.getBundleInfo().getLocation();
				AtomosBundleInfo bootBundle = atomosRuntime.getByOSGiLocation(location);
				if (bootBundle != null) {
					os.writeBoolean(true);
					os.writeUTF(bootBundle.getLocation());
				} else {
					os.writeBoolean(false);
				}
			}

			@Override
			public ModuleRevisionBuilder adaptModuleRevisionBuilder(ModuleEvent operation,
					org.eclipse.osgi.container.Module origin, ModuleRevisionBuilder builder) {
				Generation generation = getGeneration();
				AtomosBundleInfo atomosBundle = atomosRuntime.getByOSGiLocation(generation.getBundleInfo().getLocation());
				if (atomosBundle != null) {
					return createBuilder(atomosBundle, builder, hookRegistry);
				}
				return super.adaptModuleRevisionBuilder(operation, origin, builder);
			}
		};
	}

	void loadLayers(DataInputStream in) throws IOException {
		atomosRuntime.lock.writeLock().lock();
		try {
			long nextLayerId = in.readLong();
			int numLayers = in.readInt();
			for (int i = 0; i < numLayers; i++) {
				readLayer(in);
			}
			atomosRuntime.nextLayerId.set(nextLayerId);
		} finally {
			atomosRuntime.lock.writeLock().unlock();
		}
	}

	void saveLayers(DataOutputStream out) throws IOException {
		atomosRuntime.lock.readLock().lock();
		try {
			out.writeLong(atomosRuntime.nextLayerId.get());
			List<AtomosLayerImpl> writeOrder = getLayerWriteOrder((AtomosLayerImpl) atomosRuntime.getBootLayer(), new HashSet<>(), new ArrayList<>());
			out.writeInt(writeOrder.size());
			for (AtomosLayerImpl layer : writeOrder) {
				writeLayer(layer, out);
			}
		} finally {
			atomosRuntime.lock.readLock().unlock();
		}
	}

	private List<AtomosLayerImpl> getLayerWriteOrder(AtomosLayer layer, Set<AtomosLayer> visited, List<AtomosLayerImpl> result) {
		if (!visited.add(layer)) {
			return result;
		}

		// visit all parents first
		for (AtomosLayer parent : layer.getParents()) {
			getLayerWriteOrder(parent, visited, result);
		}

		// add self before children
		result.add((AtomosLayerImpl) layer);

		// now visit children
		for (AtomosLayer child : layer.getChildren()) {
			getLayerWriteOrder(child, visited, result);
		}
		return result;
	}

	private void readLayer(DataInputStream in) throws IOException {
		String name = in.readUTF();
		long id = in.readLong();
		LoaderType loaderType = LoaderType.valueOf(in.readUTF());
		int numPaths = in.readInt();
		Path[] paths = new Path[numPaths];
		for (int i = 0; i < numPaths; i++) {
			String sURI = in.readUTF();
			try {
				URI uri = new URI(sURI);
				paths[i] = Path.of(uri);
			} catch (URISyntaxException e) {
				throw new IOException(e);
			}
		}
		int numParents = in.readInt();
		List<AtomosLayer> parents = new ArrayList<>();
		for (int i = 0; i < numParents; i++) {
			long parentId = in.readLong();
			AtomosLayerImpl parent = atomosRuntime.getById(parentId);
			if (parent == null) {
				throw new IllegalArgumentException("Missing parent with id: " + parentId);
			}
			parents.add(parent);
		}
		if (atomosRuntime.getById(id) == null) {
			try {
				atomosRuntime.addLayer(parents, name, id, loaderType, paths);
			} catch (Exception e) {
				throw new IllegalArgumentException("Error adding persistent layer: " + e.getMessage());
			}
		}
	}

	private void writeLayer(AtomosLayerImpl layer, DataOutputStream out) throws IOException {
		out.writeUTF(layer.getName());
		out.writeLong(layer.getId());
		out.writeUTF(layer.getLoaderType().toString());
		List<Path> paths = layer.getPaths();
		out.writeInt(paths.size());
		for (Path path : paths) {
			out.writeUTF(path.toUri().toString());
		}
		List<AtomosLayer> parents = layer.getParents();
		out.writeInt(parents.size());
		for (AtomosLayer parent : parents) {
			out.writeLong(((AtomosLayerImpl) parent).getId());
		}
	}

	ModuleRevisionBuilder createBuilder(AtomosBundleInfo atomosBundle, ModuleRevisionBuilder original,
			HookRegistry hookRegistry) {

		if (atomosBundle.getModule().isEmpty()) {
			return null;
		}
		List<GenericInfo> origCaps = original.getCapabilities();
		List<GenericInfo> origReqs = original.getRequirements();

		ModuleDescriptor desc = atomosBundle.getModule().get().getDescriptor();
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
}
