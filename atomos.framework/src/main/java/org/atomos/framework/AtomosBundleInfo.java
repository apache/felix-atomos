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

import java.util.Optional;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * Information about an Atomos bundle.
 */
public interface AtomosBundleInfo extends Comparable<AtomosBundleInfo> {

	/**
	 * The location of the Atomos bundle. The location
	 * always includes the Atomos bundle's layer {@link AtomosLayer#getName() name}.
	 * This location plus the {@link #install(String) prefix} will be used
	 * as the install location of the Atomos bundle.
	 * 
	 * @return the location of the Atomos bundle.
	 * @see AtomosBundleInfo#install(String)
	 */
	public String getLocation();

	/**
	 * The symbolic name of the Atomos bundle.
	 * @return the symbolic name.
	 */
	public String getSymbolicName();

	/**
	 * The version of the Atomos bundle.
	 * @return the version
	 */
	public Version getVersion();

	/**
	 * The module of the Atomos bundle.  If not running
	 * in a module layer then the optional will have a null value.
	 * @return the module or null if not running in a module layer.
	 */
	public Optional<Module> getModule();

	/**
	 * The Atomos layer this Atomos bundle is in.
	 * @return the Atomos layer
	 */
	public AtomosLayer getAtomosLayer();

	/**
	 * Installs this Atomos bundle using the specified prefix.  If the Atomos bundle
	 * is already installed then the existing bundle is returned.
	 * @param prefix
	 * @return the installed Atomos bundle.
	 * @throws BundleException if an error occurs installing the Atomos bundle
	 */
	public Bundle install(String prefix) throws BundleException;

}
