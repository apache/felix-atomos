/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.atomos.framework.substrate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.base.AtomosRuntimeBase;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.BundleCapability;

/**
 * This is a quick and dirty way to get bundle entry resources to work
 * in a substrate native image.  It basically requires all bundles compiled
 * into the native image to also have a bundle JAR on disk located in the
 * substrate_lib/ folder.  Eventually we need a way to map all the resources
 * from a bundle that are not class resources into the native image.  This is
 * mainly for resources located in META-INF and OSGI-INF folders.
 *
 */
public class AtomosRuntimeSubstrate extends AtomosRuntimeBase {

	private final AtomosLayer bootLayer = createBootLayer();

	protected AtomosLayer createBootLayer() {
		lockWrite();
		try {
			AtomosLayerBase result = new AtomosLayerSubstrate(Collections.emptyList(), nextLayerId.getAndIncrement(), "boot", LoaderType.SINGLE);
			addAtomosLayer(result);
			return result;
		} finally {
			unlockWrite();
		}
	}

	@Override
	protected AtomosLayer addLayer(List<AtomosLayer> parents, String name, long id, LoaderType loaderType,
			Path... paths) {
		throw new UnsupportedOperationException("Cannot add module layers when Atomos is not loaded as module.");
	}

	@Override
	protected FrameworkFactory findFrameworkFactory() {
		Iterator<FrameworkFactory> itr = ServiceLoader.load(FrameworkFactory.class, getClass().getClassLoader()).iterator();
		if (itr.hasNext()) {
			return itr.next();
		}
		throw new RuntimeException("No Framework implementation found.");
	}

	@Override
	public boolean modulesSupported() {
		return false;
	}

	@Override
	protected void filterBasedOnReadEdges(AtomosBundleInfo atomosBundle, Collection<BundleCapability> candidates) {
		filterNotVisible(atomosBundle, candidates);
	}

	public class AtomosLayerSubstrate extends AtomosLayerBase {
		private final Set<AtomosBundleInfoBase> atomosBundles;
		protected AtomosLayerSubstrate(List<AtomosLayer> parents, long id, String name, LoaderType loaderType,
				Path... paths) {
			super(parents, id, name, loaderType, paths);
			atomosBundles = findSubstrateAtomosBundles();
		}


		@Override
		public final Set<AtomosBundleInfo> getAtomosBundles() {
			return asSet(atomosBundles);
		}

		@Override
		protected void findBootLayerAtomosBundles(Set<AtomosBundleInfoBase> result) {
			// do nothing for class path runtime case
		}

		Set<AtomosBundleInfoBase> findSubstrateAtomosBundles() {
			Set<AtomosBundleInfoBase> result = new HashSet<>();
			File substrateLib = getSubstrateLibDir();

			if (substrateLib.isDirectory()) {
				for (File f : substrateLib.listFiles()) {
					if (f.isFile()) {
						try (JarFile jar = new JarFile(f)) {
							Attributes headers = jar.getManifest().getMainAttributes();
							String symbolicName = headers.getValue(Constants.BUNDLE_SYMBOLICNAME);
							if (symbolicName != null) {
								int semiColon = symbolicName.indexOf(';');
								if (semiColon != -1) {
									symbolicName = symbolicName.substring(0, semiColon);
								}
								symbolicName = symbolicName.trim();

								ConnectContent connectContent = new SubstrateConnectContent(f.getName());
								connectContent.open();
								String location;
								if (connectContent.getEntry("META-INF/services/org.osgi.framework.launch.FrameworkFactory").isPresent()) {
									location = Constants.SYSTEM_BUNDLE_LOCATION;
								} else {
									location = f.getName();
									if (!getName().isEmpty()) {
										location = getName() + ":" + location;
									}
								}
								Version version = Version.parseVersion(headers.getValue(Constants.BUNDLE_VERSION));
								result.add(new AtomosBundleInfoSubstrate(location, symbolicName, version, connectContent));
							}
						} catch (IOException e) {
							// ignore and continue
						}
					}
				}
			}
			return result;
		}

		public class AtomosBundleInfoSubstrate extends AtomosBundleInfoBase {

			public AtomosBundleInfoSubstrate(String location, String symbolicName, Version version, ConnectContent content) {
				super(location, symbolicName, version, content);
			}

			@Override
			protected final Object getKey() {
				// TODO this will not work
				return getConnectContent();
			}
		}
	}

	@Override
	public AtomosLayer getBootLayer() {
		return bootLayer ;
	}

	@Override
	protected void addingLayer(AtomosLayerBase atomosLayer) {
		// nothing to do
	}
	@Override
	protected void removedLayer(AtomosLayerBase atomosLayer) {
		// nothing to do
	}

	@Override
	protected String getBundleLocation(Class<?> classFromBundle) {
		// TODO need to map class to bundle somehow; needed for FrameworkUtil.getBundle to work
		return null;
	}
}
