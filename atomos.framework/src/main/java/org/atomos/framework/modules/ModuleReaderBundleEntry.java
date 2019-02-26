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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.eclipse.osgi.storage.bundlefile.BundleEntry;

class ModuleReaderBundleEntry extends BundleEntry {
	private final ModuleReaderBundleFile bundleFile;
	private final String path;
	private final URI uri;
	public ModuleReaderBundleEntry(ModuleReaderBundleFile bundleFile, String path, URI uri) {
		this.bundleFile = bundleFile;
		if (path.length() > 0 && path.charAt(0) == '/') {
			path = path.substring(1);
		}
		this.path = path;
		this.uri = uri;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return bundleFile.getInputStream(path);
	}

	@Override
	public byte[] getBytes() throws IOException {
		return bundleFile.getBytes(path);
	}

	@Override
	public long getSize() {
		try {
			return uri.toURL().openConnection().getContentLengthLong();
		} catch (IOException e) {
			return 0;
		}
	}

	@Override
	public String getName() {
		return path;
	}

	@Override
	public long getTime() {
		try {
			return uri.toURL().openConnection().getDate();
		} catch (IOException e) {
			return 0;
		}
	}

	@Override
	public URL getLocalURL() {
		try {
			return uri.toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	public URL getFileURL() {
		try {
			File file = bundleFile.getFile(path, false);
			if (file != null)
				return file.toURI().toURL();
		} catch (MalformedURLException e) {
			//This can not happen. 
		}
		return null;
	}

}
