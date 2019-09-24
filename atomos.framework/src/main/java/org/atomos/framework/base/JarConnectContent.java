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
package org.atomos.framework.base;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.osgi.framework.connect.ConnectContent;

public class JarConnectContent implements ConnectContent {
	final ZipFile zipFile;

	public JarConnectContent(ZipFile zipFile) {
		this.zipFile = zipFile;
	}

	@Override
	public void open() throws IOException {
		// do nothing
	}
	@Override
	public void close() throws IOException {
		// do nothing
	}

	@Override
	public Optional<ClassLoader> getClassLoader() {
		return Optional.of(getClass().getClassLoader());
	}

	@Override
	public Iterable<String> getEntries() throws IOException {
		return () -> new Iterator<String>() {
			final Enumeration<? extends ZipEntry> entries  = zipFile.entries();
			@Override
			public boolean hasNext() {
				return entries.hasMoreElements();
			}

			@Override
			public String next() {
				return entries.nextElement().getName(); 
			}
		};
	}

	@Override
	public Optional<ConnectEntry> getEntry(String name) {
		ZipEntry entry = zipFile.getEntry(name);
		if (entry != null) {
			return Optional.of(new JarConnectEntry(entry));
		}
		return Optional.empty();
	}

	@Override
	public Optional<Map<String, String>> getHeaders() {
		return Optional.empty();
	}

	class JarConnectEntry implements ConnectEntry {
		final ZipEntry entry;

		public JarConnectEntry(ZipEntry entry) {
			this.entry = entry;
		}

		@Override
		public long getContentLength() {
			return entry.getSize();
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return zipFile.getInputStream(entry);
		}

		@Override
		public long getLastModified() {
			return entry.getTime();
		}

		@Override
		public String getName() {
			return entry.getName();
		}
		
	}
}
