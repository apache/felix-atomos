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

import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.internal.framework.EquinoxConfiguration;
import org.eclipse.osgi.internal.loader.BundleLoader;
import org.eclipse.osgi.internal.loader.ModuleClassLoader;
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager;
import org.eclipse.osgi.storage.BundleInfo.Generation;

public class AtomosClassLoader extends ModuleClassLoader {
	private static final boolean ATOM_REGISTERED_AS_PARALLEL = ClassLoader.registerAsParallelCapable();

	private volatile Generation generation;
	private volatile Debug debug;
	private volatile ClasspathManager manager;
	private volatile BundleLoader loader;
	private volatile EquinoxConfiguration configuration;
	private final boolean isRegisteredAsParallel;
	public AtomosClassLoader(ClassLoader parent) {
		super(parent);
		this.isRegisteredAsParallel = (ModuleClassLoader.REGISTERED_AS_PARALLEL && ATOM_REGISTERED_AS_PARALLEL);
	}

	private <T> T checkInitialized(T check) {
		if (check == null) {
			throw new RuntimeException("Atomos class loader has not been initialized.");
		}
		return check;
	}
	@Override
	protected Generation getGeneration() {

		return checkInitialized(generation);
	}

	@Override
	protected Debug getDebug() {
		return checkInitialized(debug);
	}

	@Override
	public ClasspathManager getClasspathManager() {
		return checkInitialized(manager);
	}

	@Override
	protected EquinoxConfiguration getConfiguration() {
		return checkInitialized(configuration);
	}

	@Override
	public BundleLoader getBundleLoader() {
		return checkInitialized(loader);
	}

	@Override
	public boolean isRegisteredAsParallel() {
		return isRegisteredAsParallel;
	}

	void init(EquinoxConfiguration config, BundleLoader loader, Generation gen) {
		this.configuration = config;
		this.debug = config.getDebug();
		this.loader = loader;
		this.generation = gen;
		this.manager = new ClasspathManager(gen, this);
	}

}
