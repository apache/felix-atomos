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

import java.lang.module.ResolvedModule;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * Information about an atomos bundle.
 */
public interface AtomosBundleInfo extends Comparable<AtomosBundleInfo> {

	/**
	 * The location of the atomos bundle.
	 * This location plus the prefix will be used
	 * to install the atomos bundle.
	 * @return the location of the atomos bundle.
	 * @see AtomosRuntime#installAtomBundle(String, org.osgi.framework.VersionRange, String)
	 */
	public String getLocation();

	/**
	 * The symbolic name of the atomos bundle.
	 * @return the symbolic name.
	 */
	public String getSymbolicName();

	/**
	 * The version of the atomos bundle.
	 * @return the version
	 */
	public Version getVersion();

	/**
	 * The resolved module of the atomos bundle.
	 * @return the resolved module
	 */
	public ResolvedModule getResolvedModule();

	/**
	 * The module of the atomos bundle.
	 * @return the module
	 */
	public Module getModule();

	/**
	 * The atomos layer this atomos bundle is in.
	 * @return the atomos layer
	 */
	public AtomosLayer getAtomLayer();

	/**
	 * Installs this atomos bundle using the specified prefix.  If the atomos bundle
	 * is already installed then the existing bundle is returned.
	 * @param prefix
	 * @return the installed atomos bundle.
	 * @throws BundleException if an error occurs installing the atomos bundle
	 */
	public Bundle install(String prefix) throws BundleException;

}
