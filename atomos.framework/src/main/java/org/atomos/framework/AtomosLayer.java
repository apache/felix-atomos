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
package org.atomos.framework;

import java.lang.module.Configuration;
import java.util.List;
import java.util.Set;

/**
 * An Atomos Layer represents a {@link ModuleLayer} that was added to
 * a {@link AtomosRuntime} using the {@link AtomosRuntime#addLayer(Configuration) addLayer}
 * method or the Atomos Layer could represent the {@link AtomosRuntime#getBootLayer() boot layer}.
 * An Atomos Layer will contain one or more {@link AtomosBundleInfo atomos bundles} which can
 * then be used to {@link AtomosBundleInfo#install(String)} them as OSGi bundles into the
 * {@code AtomosRuntime#createFramework(java.util.Map) framework}.
 */
public interface AtomosLayer {
	/**
	 * The {@link Configuration} used to create the {@link ModuleLayer} for this
	 * Atomos layer.  This configuration can be used as a parent of new 
	 * configurations which then can be used to add a new Atomos Layer by calling the
	 * {@link AtomosRuntime#addLayer(Configuration)} method.
	 * @return the configuration
	 */
	Configuration getConfiguration();

	/**
	 * The {@link ModuleLayer} associated with this Atomos Layer.
	 * @return the ModuleLayer
	 */
	ModuleLayer getModuleLayer();

	/**
	 * The Atomos Layer children of this layer
	 * @return The children of this layer
	 */
	Set<AtomosLayer> getChildren();

	/**
	 * The Atomos parents of this layer
	 * @return the parnets of this layer
	 */
	List<AtomosLayer> getParents();

	/**
	 * The Atomos bundles contained in this layer
	 * @return the Atomos Bundles
	 */
	Set<AtomosBundleInfo> getAtomosBundles();
}
