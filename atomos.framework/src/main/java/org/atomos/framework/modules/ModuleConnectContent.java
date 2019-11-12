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
package org.atomos.framework.modules;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.connect.ConnectContent;

public class ModuleConnectContent implements ConnectContent {
	final Module module;
	final ModuleReference reference;
	final AtomosRuntimeModules atomosRuntime;
	final AtomicReference<Optional<Map<String, String>>> headers = new AtomicReference<>();
	volatile ModuleReader reader = null;

	public ModuleConnectContent(Module module, ModuleReference reference, AtomosRuntimeModules atomosRuntime) {
		this.module = module;
		this.reference = reference;
		this.atomosRuntime = atomosRuntime;
	}

	@Override
	public void open() throws IOException {
		reader = reference.open();
	}
	@Override
	public void close() throws IOException {
		ModuleReader current = reader;
		if (current != null) {
			reader = null;
			current.close();
		}
	}

	@Override
	public Optional<ClassLoader> getClassLoader() {
		return Optional.ofNullable(module.getClassLoader());
	}

	@Override
	public Iterable<String> getEntries() throws IOException {
		return () -> {
			try {
				return currentReader().list().iterator();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		};

	}

	private ModuleReader currentReader() throws IOException {
		ModuleReader current = reader;
		if (current == null) {
			throw new IOException("Reader is not open.");
		}
		return current;
	}

	@Override
	public Optional<ConnectEntry> getEntry(String name) {
		try {
			return currentReader().find(name).map((u) -> new ModuleConnectEntry(name, u));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

	}

	@Override
	public Optional<Map<String, String>> getHeaders() {
		return headers.updateAndGet((h) -> {
			if (h == null) {
				h = atomosRuntime.createManifest(this, module);
			}
			return h;
		});
	}

	class ModuleConnectEntry implements ConnectEntry {
		final String name;
		final URI uri;

		public ModuleConnectEntry(String name, URI uri) {
			this.name = name;
			this.uri = uri;
		}

		@Override
		public long getContentLength() {
			try {
				return uri.toURL().openConnection().getContentLengthLong();
			} catch (IOException e) {
				return 0;
			}
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return currentReader().open(name).get();
		}

		@Override
		public long getLastModified() {
			try {
				return uri.toURL().openConnection().getDate();
			} catch (IOException e) {
				return 0;
			}
		}

		@Override
		public String getName() {
			return name;
		}
		
	}
}
