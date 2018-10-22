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

import java.io.File;
import java.io.IOException;
import java.lang.module.ResolvedModule;
import java.util.Optional;
import java.util.jar.JarFile;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.eclipse.osgi.storage.bundlefile.DirBundleFile;
import org.eclipse.osgi.storage.bundlefile.MRUBundleFileList;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * Information about an atomos bundle.
 */
public class AtomosBundleInfoImpl implements AtomosBundleInfo, Comparable<AtomosBundleInfo> {
	/**
	 * The resolved module for this atomos bundle
	 */
	private final Optional<ResolvedModule> resolvedModule;

	/**
	 * The module for this atomos bundle.
	 */
	private final Optional<Module> module;

	private final Optional<JarFile> jarFile;
	private final Optional<File> file;

	/**
	 * The bundle location used to install the bundle with.
	 */
	private final String location;

	/**
	 * Bundle symbolic name
	 */
	private final String symbolicName;

	/**
	 * Bundle version 
	 */
	private final Version version;

	private final AtomosRuntimeImpl runtime;
	private final AtomosLayer atomosLayer;

	public AtomosBundleInfoImpl(AtomosRuntimeImpl runtime, AtomosLayer atomosLayer, ResolvedModule resolvedModule, Module module, String location, String symbolicName, Version version) {
		this(runtime, atomosLayer, resolvedModule, module, null, null, location, symbolicName, version);
	}

	public AtomosBundleInfoImpl(AtomosRuntimeImpl runtime, AtomosLayer atomosLayer, JarFile jarFile, String location, String symbolicName, Version version) {
		this(runtime, atomosLayer, null, null, jarFile, null, location, symbolicName, version);
	}

	public AtomosBundleInfoImpl(AtomosRuntimeImpl runtime, AtomosLayer atomosLayer, File file, String location, String symbolicName, Version version) {
		this(runtime, atomosLayer, null, null, null, file, location, symbolicName, version);
	}

	private AtomosBundleInfoImpl(AtomosRuntimeImpl runtime, AtomosLayer atomosLayer, ResolvedModule resolvedModule, Module module, JarFile jarFile, File file, String location, String symbolicName, Version version) {
		this.runtime = runtime;
		this.atomosLayer = atomosLayer;
		this.resolvedModule = Optional.ofNullable(resolvedModule);
		this.module = Optional.ofNullable(module);
		this.location = location;
		this.symbolicName = symbolicName;
		this.version = version;
		this.jarFile = Optional.ofNullable(jarFile);
		this.file = Optional.ofNullable(file);
	}
	@Override
	public String getLocation() {
		return location;
	}

	@Override
	public String getSymbolicName() {
		return symbolicName;
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public Optional<Module> getModule() {
		return module;
	}

	@Override
	public AtomosLayer getAtomosLayer() {
		return atomosLayer;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AtomosBundleInfoImpl)) {
			return false;
		}
		AtomosBundleInfoImpl info = (AtomosBundleInfoImpl) o;
		return getSymbolicName().equals(info.getSymbolicName()) && getVersion().equals(info.getVersion());
	}

	@Override
	public int hashCode() {
		return getSymbolicName().hashCode() ^ getVersion().hashCode();
	}

	@Override
	public int compareTo(AtomosBundleInfo o) {
		int bsnCompare = getSymbolicName().compareTo(o.getSymbolicName());
		if (bsnCompare != 0) {
			return bsnCompare;
		}
		int vCompare = -(getVersion().compareTo(o.getVersion()));
		if (vCompare != 0) {
			return vCompare;
		}
		return getLocation().compareTo(o.getLocation());
	}

	BundleFile getBundleFile(BundleFile bundleFile, Generation generation, MRUBundleFileList mruList, Debug debug) throws IOException {
		if (resolvedModule.isPresent()) {
			return new ModuleReaderBundleFile(resolvedModule.get().reference(), bundleFile.getBaseFile(), generation, mruList, debug);
		}
		if (file.isPresent()) {
			return new DirBundleFile(file.get(), true);
		}
		if (jarFile.isPresent()) {
			return new JarBundleFile(jarFile.get(), bundleFile.getBaseFile(), generation, debug);
		}
		throw new IllegalStateException("No content available for the bundle file.");
	}

	public String toString() {
		return symbolicName;
	}

	@Override
	public Bundle install(String prefix) throws BundleException {
		return runtime.installAtomosBundle(prefix, this);
	}
}
