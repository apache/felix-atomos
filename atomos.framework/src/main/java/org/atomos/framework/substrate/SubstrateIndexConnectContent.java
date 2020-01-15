package org.atomos.framework.substrate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.atomos.framework.base.AtomosRuntimeBase;
import org.osgi.framework.connect.ConnectContent;

public class SubstrateIndexConnectContent implements ConnectContent {
	static class URLConnectEntry implements ConnectEntry {
		private final String name;
		private final URL resource;

		URLConnectEntry(String name, URL resource) {
			this.name = name;
			this.resource = resource;
		}
		@Override
		public String getName() {
			return name;
		}

		@Override
		public long getContentLength() {
			try {
				return resource.openConnection().getContentLengthLong();
			} catch (IOException e) {
				return -1;
			}
		}

		@Override
		public long getLastModified() {
			try {
				return resource.openConnection().getDate();
			} catch (IOException e) {
				return 0;
			}
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return resource.openStream();
		}
		
	}

	final String index;
	final List<String> entries;

	SubstrateIndexConnectContent(String index, List<String> entries) {
		this.index = index;
		this.entries = Collections.unmodifiableList(entries);
	}

	@Override
	public Optional<Map<String, String>> getHeaders() {
		return Optional.empty();
	}

	@Override
	public Iterable<String> getEntries() throws IOException {
		return entries;
	}

	@Override
	public Optional<ConnectEntry> getEntry(String name) {
		if (entries.contains(name)) {
			URL resource = getClass().getResource(AtomosRuntimeBase.ATOMOS_BUNDLES + index + '/' + name);
			if (resource != null) {
				return Optional.of(new URLConnectEntry(name, resource));
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<ClassLoader> getClassLoader() {
		return Optional.of(getClass().getClassLoader());
	}

	@Override
	public ConnectContent open() throws IOException {
		// do nothing
		return this;
	}

	@Override
	public ConnectContent close() throws IOException {
		// do nothing
		return this;
	}

}
