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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

import org.atomos.framework.base.AtomosRuntimeBase.AtomosLayerBase.AtomosBundleInfoBase;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.*;

public class AtomosBundleFileWrapper extends BundleFileWrapper {
	private final BundleFile bootContent;

	public AtomosBundleFileWrapper(AtomosBundleInfoBase bootBundle, BundleFile bundleFile, Generation generation, MRUBundleFileList mruList, Debug debug) throws IOException {
		super(bundleFile);
		bootContent = bootBundle.getBundleFile(bundleFile, generation, mruList, debug);
	}

	@Override
	public BundleEntry getEntry(final String path) {
		return bootContent.getEntry(path);
	}

	@Override
	public File getFile(String path, boolean nativeCode) {
		return bootContent.getFile(path, nativeCode);
	}

	@Override
	public Enumeration<String> getEntryPaths(String path) {
		return bootContent.getEntryPaths(path);
	}

	@Override
	public Enumeration<String> getEntryPaths(String path, boolean recurse) {
		return bootContent.getEntryPaths(path, recurse);
	}

	@Override
	public boolean containsDir(String dir) {
		return bootContent.containsDir(dir);
	}
}
