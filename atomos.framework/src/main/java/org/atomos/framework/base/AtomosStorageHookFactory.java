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
package org.atomos.framework.base;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime.LoaderType;
import org.atomos.framework.base.AtomosRuntimeBase.AtomosLayerBase;
import org.atomos.framework.base.AtomosRuntimeBase.AtomosLayerBase.AtomosBundleInfoBase;
import org.eclipse.osgi.container.ModuleContainerAdaptor.ModuleEvent;
import org.eclipse.osgi.container.ModuleRevisionBuilder;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.hookregistry.StorageHookFactory;
import org.eclipse.osgi.internal.hookregistry.StorageHookFactory.StorageHook;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.osgi.framework.BundleException;

public class AtomosStorageHookFactory extends StorageHookFactory<AtomicBoolean, AtomicBoolean, StorageHook<AtomicBoolean, AtomicBoolean>> {
	static final String OSGI_CONTRACT_NAMESPACE = "osgi.contract";
	static final String OSGI_VERSION_ATTR = "version:Version";
	private final AtomosRuntimeBase atomosRuntime;
	private final HookRegistry hookRegistry;
	
	public AtomosStorageHookFactory(AtomosRuntimeBase atomosRuntime, HookRegistry hookRegistry) {
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
					AtomosBundleInfoBase atomosBundle = atomosRuntime.getByAtomosLocation(atomosLocation);
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
				AtomosBundleInfoBase atomosBundle = atomosRuntime.getByOSGiLocation(generation.getBundleInfo().getLocation());
				if (atomosBundle != null) {
					return createBuilder(atomosBundle, builder, hookRegistry);
				}
				return super.adaptModuleRevisionBuilder(operation, origin, builder);
			}
		};
	}

	void loadLayers(DataInputStream in) throws IOException {
		atomosRuntime.lockWrite();
		try {
			long nextLayerId = in.readLong();
			int numLayers = in.readInt();
			for (int i = 0; i < numLayers; i++) {
				readLayer(in);
			}
			atomosRuntime.nextLayerId.set(nextLayerId);
		} finally {
			atomosRuntime.unlockWrite();
		}
	}

	void saveLayers(DataOutputStream out) throws IOException {
		atomosRuntime.lockRead();
		try {
			out.writeLong(atomosRuntime.nextLayerId.get());
			List<AtomosLayerBase> writeOrder = getLayerWriteOrder((AtomosLayerBase) atomosRuntime.getBootLayer(), new HashSet<>(), new ArrayList<>());
			out.writeInt(writeOrder.size());
			for (AtomosLayerBase layer : writeOrder) {
				writeLayer(layer, out);
			}
		} finally {
			atomosRuntime.unlockRead();
		}
	}

	private List<AtomosLayerBase> getLayerWriteOrder(AtomosLayer layer, Set<AtomosLayer> visited, List<AtomosLayerBase> result) {
		if (!visited.add(layer)) {
			return result;
		}

		// visit all parents first
		for (AtomosLayer parent : layer.getParents()) {
			getLayerWriteOrder(parent, visited, result);
		}

		// add self before children
		result.add((AtomosLayerBase) layer);

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
				// TODO on Java 11 should use Path.of()
				paths[i] = new File(uri).toPath();
			} catch (URISyntaxException e) {
				throw new IOException(e);
			}
		}
		int numParents = in.readInt();
		List<AtomosLayer> parents = new ArrayList<>();
		for (int i = 0; i < numParents; i++) {
			long parentId = in.readLong();
			AtomosLayerBase parent = atomosRuntime.getById(parentId);
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

	private void writeLayer(AtomosLayerBase layer, DataOutputStream out) throws IOException {
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
			out.writeLong(((AtomosLayerBase) parent).getId());
		}
	}

	ModuleRevisionBuilder createBuilder(AtomosBundleInfoBase atomosBundle, ModuleRevisionBuilder original,
			HookRegistry hookRegistry) {
		return atomosRuntime.createBuilder(atomosBundle, original, hookRegistry);
	}
}
