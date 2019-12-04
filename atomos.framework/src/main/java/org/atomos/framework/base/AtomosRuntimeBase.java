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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.atomos.framework.base.AtomosRuntimeBase.AtomosLayerBase.AtomosBundleInfoBase;
import org.atomos.framework.substrate.AtomosRuntimeSubstrate;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.connect.ConnectFramework;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.connect.FrameworkUtilHelper;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.FrameworkWiring;

public abstract class AtomosRuntimeBase implements AtomosRuntime, SynchronousBundleListener, FrameworkUtilHelper {
	static final boolean DEBUG = false;
	static final String JAR_PROTOCOL = "jar";
	static final String FILE_PROTOCOL = "file";
	public static final String ATOMOS_BUNDLES = "/atomos/";
	public static final String ATOMOS_BUNDLES_INDEX = ATOMOS_BUNDLES + "bundles.index";
	public static final String ATOMOS_SUBSTRATE = "atomos.substrate";
	public static final String ATOMOS_RUNTIME_CLASS = "atomos.runtime.class";
	public static final String ATOMOS_RUNTIME_MODULES_CLASS = "org.atomos.framework.modules.AtomosRuntimeModules";
	public static final String SUBSTRATE_LIB_DIR = "substrate_lib";
	public static final String GRAAL_NATIVE_IMAGE_KIND = "org.graalvm.nativeimage.kind";

	private final AtomicReference<BundleContext> context = new AtomicReference<>();
	private final AtomicReference<File> storeRoot = new AtomicReference<>();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	// The following area all protected by the read/write lock
	
	// A map of Atomos bundles that are installed as OSGi bundles; the key is the OSGi bundle location
	private final Map<String, AtomosBundleInfoBase> osgiLocationToAtomosBundle = new HashMap<>();
	// A map of all Atomos bundles discovered (may not be installed as OSGi bundles); the key is the Atomos location
	private final Map<String, AtomosBundleInfoBase> atomosLocationToAtomosBundle = new HashMap<>();
	// A map of Atomos bundle OSGi bundle locations that are installed as OSGi bundles for an AtomosLayer; value is the OSGi bundle locations
	final Map<AtomosLayer, Collection<String>> atomosLayerToOSGiLocations = new HashMap<>();
	// A map of OSGi bundle locations for installed Atomos bundles; key is the AtomosBundleInfoBase.getKey()
	// Used to lookup an OSGi bundle location for a Class<?> in getBundleLocation(Class<?>)
	protected final Map<Object, String> atomosKeyToOSGiLocation = new HashMap<>();
	// A map of Layers keyed by layer ID
	final Map<Long, AtomosLayerBase> idToLayer = new HashMap<>();
	// A map of OSGi bundle locations for installed Atomos bundles; key is Atomos bundle
	private final Map<AtomosBundleInfo, String> atomosBundleToOSGiLocation = new HashMap<>();
	protected final AtomicLong nextLayerId = new AtomicLong(0);

	public static AtomosRuntime newAtomosRuntime() {
		String runtimeClass = System.getProperty(ATOMOS_RUNTIME_CLASS);
		if (runtimeClass != null) {
			return loadRuntime(runtimeClass);
		}
		if (System.getProperty(ATOMOS_SUBSTRATE) != null || System.getProperty(GRAAL_NATIVE_IMAGE_KIND) != null) {
			URL index = AtomosRuntimeBase.class.getResource(ATOMOS_BUNDLES_INDEX);
			if (index != null) {
				return new AtomosRuntimeSubstrate(null);
			}
			File substrateLibDir = findSubstrateLibDir();
			if (substrateLibDir.isDirectory()) {
				return new AtomosRuntimeSubstrate(substrateLibDir);
			} else {
				throw new IllegalStateException("No substrate_lib directory found.");
			}
		}
		try {
			Class.forName("java.lang.Module");
			return loadRuntime(ATOMOS_RUNTIME_MODULES_CLASS);
		} catch (ClassNotFoundException e) {
			// ignore
		}
		// default to classpath
		return new AtomosRuntimeClassPath();
	}

	private static AtomosRuntime loadRuntime(String runtimeClass) {
		try {
			return (AtomosRuntimeBase) Class.forName(runtimeClass).getConstructor().newInstance();
		} catch (Exception e) {
			throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
		}
	}

	public static File findSubstrateLibDir() {
		String substrateProp = System.getProperty(ATOMOS_SUBSTRATE);
		File result = new File (substrateProp, SUBSTRATE_LIB_DIR);
		return result;
	}

	protected AtomosRuntimeBase() {
	}

	protected final void lockWrite() {
		lock.writeLock().lock();
	}
	protected final void unlockWrite() {
		lock.writeLock().unlock();
	}
	protected final void lockRead() {
		lock.readLock().lock();
	}
	protected final void unlockRead() {
		lock.readLock().unlock();
	}

	protected final AtomosBundleInfoBase getByOSGiLocation(String location) {
		lockRead();
		try {
			return osgiLocationToAtomosBundle.get(location);
		} finally {
			unlockRead();
		}
	}

	final void addToInstalledBundles(String osgiLocation, AtomosBundleInfoBase atomosBundle) {
		lockWrite();
		try {
			osgiLocationToAtomosBundle.put(osgiLocation, atomosBundle);
			atomosBundleToOSGiLocation.put(atomosBundle, osgiLocation);
			AtomosLayer layer = atomosBundle.getAtomosLayer();
			atomosLayerToOSGiLocations.computeIfAbsent(layer, (l) -> new ArrayList<>()).add(osgiLocation);
			atomosKeyToOSGiLocation.put(atomosBundle.getKey(), osgiLocation);
		} finally {
			unlockWrite();
		}
	}

	private void removeFromInstalledBundles(String osgiLocation) {
		lockWrite();
		try {
			AtomosBundleInfoBase removed = osgiLocationToAtomosBundle.remove(osgiLocation);
			atomosBundleToOSGiLocation.remove(removed);
			atomosLayerToOSGiLocations.computeIfAbsent(removed.getAtomosLayer(), (l) -> new ArrayList<>()).remove(osgiLocation);
			atomosKeyToOSGiLocation.remove(removed.getKey());
		} finally {
			unlockWrite();
		}
	}

	final AtomosBundleInfoBase getByAtomosLocation(String location) {
		lockRead();
		try {
			return atomosLocationToAtomosBundle.get(location);
		} finally {
			unlockRead();
		}
	}

	protected final AtomosLayerBase getById(long id) {
		lockRead();
		try {
			return idToLayer.get(id);
		} finally {
			unlockRead();
		}
	}

	final String getByAtomosBundleInfo(AtomosBundleInfo atomosBundle) {
		lockRead();
		try {
			return atomosBundleToOSGiLocation.get(atomosBundle);
		} finally {
			unlockRead();
		}
	}

	final Collection<String> getInstalledLocations(AtomosLayer layer) {
		lockRead();
		try {
			return new ArrayList<>(atomosLayerToOSGiLocations.computeIfAbsent(layer, (l) -> new ArrayList<>()));
		} finally {
			unlockRead();
		}
	}

	@Override
	public final AtomosBundleInfo getAtomosBundle(String bundleLocation) {
		return getByOSGiLocation(bundleLocation);
	}

	@Override
	public final Bundle getBundle(AtomosBundleInfo atomosBundle) {
		String location = getByAtomosBundleInfo(atomosBundle);
		if (location != null) {
			BundleContext bc = context.get();
			if (bc != null) {
				return bc.getBundle(location);
			}
		}
		return null;
	}

	@Override
	public final AtomosLayer addLayer(List<AtomosLayer> parents, String name, LoaderType loaderType, Path... paths) {
		return addLayer(parents, name, -1, loaderType, paths);
	}

	abstract protected AtomosLayer addLayer(List<AtomosLayer> parents, String name, long id, LoaderType loaderType, Path... paths);

	@Override
	public Framework newFramework(Map<String, String> frameworkConfig) {
		frameworkConfig = frameworkConfig == null ? new HashMap<>() : new HashMap<>(frameworkConfig);
		if (modulesSupported() && frameworkConfig.get(Constants.FRAMEWORK_SYSTEMPACKAGES) == null) {
			// this is to prevent the framework from exporting all the packages provided
			// by the module path.
			frameworkConfig.put(Constants.FRAMEWORK_SYSTEMPACKAGES, "");
		}
		if (frameworkConfig.get("osgi.console") == null) {
			// Always allow the console to work
			frameworkConfig.put("osgi.console", "");
		}
		return findFrameworkFactory().newFramework(frameworkConfig, newConnectFramework());
	}

	@Override
	public ConnectFramework newConnectFramework() {
		return new AtomosConnectFactory(this);
	}

	abstract protected ConnectFrameworkFactory findFrameworkFactory();

	protected final BundleContext getBundleContext() {
		return context.get();
	}

	final ThreadLocal<AtomosBundleInfo> installingInfo = new ThreadLocal<>();
	final Bundle installAtomosBundle(String prefix, AtomosBundleInfo atomosBundle) throws BundleException {
		if (prefix == null) {
			prefix = "atomos";
		}
		if (prefix.indexOf(':') != -1) {
			throw new IllegalArgumentException("The prefix cannot contain ':'");
		}
		prefix = prefix + ':';
		if (AtomosRuntimeBase.DEBUG) {
			System.out.println("Installing atomos bundle: " + prefix + atomosBundle.getLocation()); //$NON-NLS-1$
		}

		BundleContext bc = context.get();
		if (bc == null) {
			throw new IllegalStateException("Framework has not been initialized.");
		}

		String existingLoc = getByAtomosBundleInfo(atomosBundle);
		if (existingLoc != null) {
			Bundle existing = bc.getBundle(existingLoc);
			if (existing != null) {
				if(Constants.SYSTEM_BUNDLE_LOCATION.equals(existingLoc) || existingLoc.startsWith(prefix)) {
					return existing;
				}
				throw new BundleException("Atomos bundle is already installed with bundle: " + existing, BundleException.DUPLICATE_BUNDLE_ERROR);
			}
		}

		String location = atomosBundle.getLocation();
		if (!Constants.SYSTEM_BUNDLE_LOCATION.equals(location)) {
			location = prefix + atomosBundle.getLocation();
		}
		AtomosLayerBase atomosLayer = (AtomosLayerBase) atomosBundle.getAtomosLayer();
		if (!atomosLayer.isValid()) {
			throw new BundleException("Atomos layer has been uninstalled.", BundleException.INVALID_OPERATION);
		}

		addToInstalledBundles(location, (AtomosBundleInfoBase) atomosBundle);
		installingInfo.set(atomosBundle);
		Bundle result = null;
		try {
			result = bc.installBundle(location, new ByteArrayInputStream(new byte[0]));
		} finally {
			installingInfo.set(null);
			// check if the layer is still valid
			if (!atomosLayer.isValid()) {
				// The atomosLayer became invalid while installing
				if (result != null) {
					result.uninstall();
					result = null;
				}
			} else if (result == null) {
				removeFromInstalledBundles(location);
			}
		}
		return result;
	}

	final AtomosBundleInfo currentlyInstalling() {
		return installingInfo.get();
	}

	@Override
	public final void bundleChanged(BundleEvent event) {
		if (event.getType() == BundleEvent.UNINSTALLED) {
			removeFromInstalledBundles(event.getBundle().getLocation());
		}
	}

	protected final void addAtomosLayer(AtomosLayerBase atomosLayer) {
		addingLayer(atomosLayer);
		if (idToLayer.putIfAbsent(atomosLayer.getId(), atomosLayer) != null) {
			throw new IllegalStateException("AtomosLayer already exists for id: " + atomosLayer.getId());
		}

		for (AtomosBundleInfo atomosBundle : atomosLayer.getAtomosBundles()) {
			if (atomosLocationToAtomosBundle.putIfAbsent(atomosBundle.getLocation(), (AtomosBundleInfoBase) atomosBundle) != null) {
				throw new IllegalStateException("Atomos bundle location already exists: " + atomosBundle.getLocation());
			}
			if (Constants.SYSTEM_BUNDLE_LOCATION.equals(atomosBundle.getLocation())) {
				// system bundle location is always marked as installed
				addToInstalledBundles(Constants.SYSTEM_BUNDLE_LOCATION, (AtomosBundleInfoBase) atomosBundle);
			}
		}
		for (AtomosLayer parent : atomosLayer.getParents()) {
			((AtomosLayerBase) parent).addChild(atomosLayer);
		}
	}

	abstract protected void addingLayer(AtomosLayerBase atomosLayer);

	abstract protected void removedLayer(AtomosLayerBase atomosLayer);

	abstract public class AtomosLayerBase implements AtomosLayer {
		private final long id;
		private final String name;
		private final LoaderType loaderType;
		private final List<AtomosLayer> parents;
		private final Set<AtomosLayer> children = new HashSet<>();
		private final List<Path> paths;
		private volatile boolean valid = true;
		private volatile Map<String, AtomosBundleInfo> nameToBundle;

		public AtomosLayerBase(List<AtomosLayer> parents, long id, String name, LoaderType loaderType, Path... paths) {
			this.id = id;
			this.name = name == null ? "" : name;
			this.paths = Arrays.asList(paths);
			this.parents = parents;
			this.loaderType = loaderType;
		}

		protected final void addChild(AtomosLayerBase child) {
			children.add(child);
		}

		protected final void removeChild(AtomosLayerBase child) {
			children.remove(child);
		}

		protected Set<AtomosBundleInfoBase> findClassPathAtomosBundles() {
			// first get the boot modules
			Set<AtomosBundleInfoBase> bootBundles = new HashSet<>();
			findBootLayerAtomosBundles(bootBundles);
			try {
				ClassLoader cl = getClass().getClassLoader();
				Set<URL> parentManifests = new HashSet<>();
				if(cl.getParent() != null) {
					Enumeration<URL> eParentManifests = cl.getParent().getResources(JarFile.MANIFEST_NAME);
					while (eParentManifests.hasMoreElements()) {
						parentManifests.add(eParentManifests.nextElement());
					}
				}
				Enumeration<URL> classpathManifests = cl.getResources(JarFile.MANIFEST_NAME);
				while (classpathManifests.hasMoreElements()) {
					URL manifest = classpathManifests.nextElement();
					if (parentManifests.contains(manifest)) {
						// ignore parent manifests
						continue;
					}
					Attributes headers = new Manifest(manifest.openStream()).getMainAttributes();
					String symbolicName = headers.getValue(Constants.BUNDLE_SYMBOLICNAME);
					if (symbolicName != null) {
						int semiColon = symbolicName.indexOf(';');
						if (semiColon != -1) {
							symbolicName = symbolicName.substring(0, semiColon);
						}
						symbolicName = symbolicName.trim();

						Object content = getBundleContent(manifest);
						if (content != null) {
							ConnectContent connectContent;
							URL url;
							if (content instanceof File) {
								connectContent = new FileConnectContent((File) content);
								url = ((File) content).toURI().toURL();
								
							} else {
								connectContent = new JarConnectContent((JarFile) content);
								url = new File(((JarFile) content).getName()).toURI().toURL();
							}

							String location;
							if (connectContent.getEntry("META-INF/services/org.osgi.framework.launch.FrameworkFactory").isPresent()) {
								location = Constants.SYSTEM_BUNDLE_LOCATION;
							} else {
								location = content instanceof File ? ((File) content).getPath() : ((JarFile) content).getName();
								if (!getName().isEmpty()) {
									location = getName() + ":" + location;
								}
							}
							Version version = Version.parseVersion(headers.getValue(Constants.BUNDLE_VERSION));

							bootBundles.add(new AtomosBundleInfoClassPath(location, symbolicName, version, connectContent, url));
						}
					}
				}
			} catch (IOException e) {
				throw new IllegalStateException("Error finding class path bundles.", e);
			}
			return Collections.unmodifiableSet(bootBundles);
		}

		protected abstract void findBootLayerAtomosBundles(Set<AtomosBundleInfoBase> result);

		/**
		 * Returns the bundle content that contains the specified manifest URL.
		 * The return type will be a JarFile or a File for an exploded bundle.
		 * @param manifest the manifest URL to get the bundle content for
		 * @return a JarFile or File
		 */
		private Object getBundleContent(URL manifest) {
			if (JAR_PROTOCOL.equals(manifest.getProtocol())) {
				// Use a connection to get the JarFile this avoids having to parse the jar: URL
				// For spring loader they support nested jars with additional !/
				// For example: 
				//   jar:file:/path/to/out.jar!/path/to/inner.jar!/META-INF/MANIFEST.MF
				// Instead of dealing with that just get the JarFile directly that supports this
				// embedded jar stuff
				try {
					URLConnection conn = manifest.openConnection();
					if (conn instanceof JarURLConnection) {
						return ((JarURLConnection) conn).getJarFile();
					}
				} catch (IOException e) {
					// TODO log?
				}
				// TODO either log or add tracing to help debug issues
			} else if (FILE_PROTOCOL.equals(manifest.getProtocol())) {
				try {
					File f = new File(manifest.toURI());
					// return two parents up from the manifest file
					return f.getParentFile().getParentFile();
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
			}
			return null;
		}


		@Override
		public final String getName() {
			return name;
		}

		@Override
		public final Set<AtomosLayer> getChildren() {
			lockRead();
			try {
				return new HashSet<>(children);
			} finally {
				unlockRead();
			}
		}

		@Override
		public final List<AtomosLayer> getParents() {
			return parents;
		}

		final List<Path> getPaths() {
			return paths;
		}

		@Override
		public final long getId() {
			return id;
		}

		@Override
		public final LoaderType getLoaderType() {
			return loaderType;
		}

		@Override
		public final void uninstall() throws BundleException {
			List<Bundle> uninstalledBundles = new ArrayList<>();
			BundleContext bc = getBundleContext();
			if (bc != null) {
				uninstallLayer(uninstalledBundles, bc);
			}

			lockWrite();
			try {
				// now remove the layer from the runtime
				removeLayerFromRuntime();
			} finally {
				unlockWrite();
			}

			if (bc != null) {
				// now refresh any uninstalled bundles
				bc.getBundle(Constants.SYSTEM_BUNDLE_LOCATION).adapt(FrameworkWiring.class).refreshBundles(uninstalledBundles);
			}
		}

		private void removeLayerFromRuntime() {
			for (AtomosLayer parent : getParents()) {
				((AtomosLayerBase) parent).removeChild(this);
			}
			for (AtomosLayer child : getChildren()) {
				((AtomosLayerBase) child).removeLayerFromRuntime();
			}
			atomosLayerToOSGiLocations.remove(this);
			idToLayer.remove(getId());
			removedLayer(this);
		}

		final void uninstallLayer(List<Bundle> uninstalledBundles, BundleContext bc) throws BundleException {
			// mark as invalid first to prevent installs
			valid = false;
			if (getBootLayer().equals(this)) {
				throw new UnsupportedOperationException("Cannot uninstall the boot layer.");
			}
			// first uninstall all children
			for (AtomosLayer child : getChildren()) {
				((AtomosLayerBase) child).uninstallLayer(uninstalledBundles, bc);
			}
			uninstallBundles(uninstalledBundles, bc);
		}

		final boolean isValid() {
			return valid;
		}

		private void uninstallBundles(List<Bundle> uninstalled, BundleContext bc) throws BundleException {
			for (String installLoc : getInstalledLocations(this)) {
				Bundle b = bc.getBundle(installLoc);
				if (b != null) {
					uninstalled.add(b);
					b.uninstall();
				}
			}
		}

		@Override
		public final String toString() {
			StringBuilder result = new StringBuilder();
			result.append('[').append(getId()).append(']');
			result.append(' ').append(getName());
			result.append(' ').append(getLoaderType());
			List<AtomosLayer> parents = getParents();
			if (!parents.isEmpty()) {
				result.append(" PARENTS: {");
				for (AtomosLayer parent : parents) {
					result.append("[").append(parent.getId()).append(']');
					result.append(' ').append(parent.getName()).append(", ");				
				}
				result.delete(result.length() - 2, result.length());
				result.append("}");
			}
			Set<AtomosLayer> children = getChildren();
			if (!children.isEmpty()) {
				result.append(" CHILDREN: {");
				for (AtomosLayer child : getChildren()) {
					result.append("[").append(child.getId()).append(']');
					result.append(' ').append(child.getName()).append(", ");				
				}
				result.delete(result.length() - 2, result.length());
				result.append("}");
			}
			return result.toString();
		}

		@Override
		public <T> Optional<T> adapt(Class<T> type) {
			// do nothing by default
			return Optional.empty();
		}

		@Override
		public Optional<AtomosBundleInfo> findAtomosBundle(String symbolicName) {
			Map<String, AtomosBundleInfo> nameToBundle = this.nameToBundle;
			if (nameToBundle == null) {
				nameToBundle = new HashMap<>();
				final Map<String, AtomosBundleInfo> populate = nameToBundle;
				getAtomosBundles().forEach((a) -> populate.putIfAbsent(a.getSymbolicName(), a));

				Set<AtomosLayer> visited = new HashSet<>();
				Deque<AtomosLayer> stack = new ArrayDeque<>();
				visited.add(this);
				stack.push(this);

				while (!stack.isEmpty()) {
					AtomosLayer layer = stack.pop();
					layer.getAtomosBundles().forEach((a) -> populate.putIfAbsent(a.getSymbolicName(), a));
					List<AtomosLayer> parents = layer.getParents();
					for (int i = parents.size() - 1; i >= 0; i--) {
						AtomosLayer parent = parents.get(i);
						if (!visited.contains(parent)) {
							visited.add(parent);
							stack.push(parent);
						}
					}
				}

				this.nameToBundle = populate;
			}
			return Optional.ofNullable(nameToBundle.get(symbolicName));
		}

		/**
		 * Information about an atomos bundle.
		 */
		public abstract class AtomosBundleInfoBase implements AtomosBundleInfo, Comparable<AtomosBundleInfo> {

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

			/**
			 * Connect content
			 */
			private final ConnectContent content;

			public AtomosBundleInfoBase(String location, String symbolicName, Version version, ConnectContent content) {
				this.location = location;
				this.symbolicName = symbolicName;
				this.version = version;
				this.content = content;
			}

			@Override
			public final String getLocation() {
				return location;
			}

			@Override
			public final String getSymbolicName() {
				return symbolicName;
			}

			@Override
			public final Version getVersion() {
				return version;
			}

			@Override
			public <T> Optional<T> adapt(Class<T> type) {
				return Optional.empty();
			}

			@Override
			public final AtomosLayer getAtomosLayer() {
				return AtomosLayerBase.this;
			}

			@Override
			public final boolean equals(Object o) {
				if (!(o instanceof AtomosBundleInfoBase)) {
					return false;
				}
				AtomosBundleInfoBase info = (AtomosBundleInfoBase) o;
				return getSymbolicName().equals(info.getSymbolicName()) && getVersion().equals(info.getVersion()) && getAtomosLayer() == info.getAtomosLayer();
			}

			@Override
			public final int hashCode() {
				return getSymbolicName().hashCode() ^ getVersion().hashCode();
			}

			@Override
			public final int compareTo(AtomosBundleInfo o) {
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

			protected abstract Object getKey();

			@Override
			public ConnectContent getConnectContent() {
				return content;
			}

			public final String toString() {
				return symbolicName;
			}

			@Override
			public final Bundle install(String prefix) throws BundleException {
				return installAtomosBundle(prefix, this);
			}
		}

		public class AtomosBundleInfoClassPath extends AtomosBundleInfoBase {

			private final URL contentURL;


			public AtomosBundleInfoClassPath(String location, String symbolicName, Version version, ConnectContent connectContent, URL url) {
				super(location, symbolicName, version, connectContent);
				this.contentURL = url;
			}

			@Override
			protected final Object getKey() {
				return contentURL;
			}
		}
	}

	@Override
	public final Optional<Bundle> getBundle(Class<?> classFromBundle) {
		String location = getBundleLocation(classFromBundle);
		if (location != null) {
			BundleContext bc = context.get();
			if (bc != null) {
				return Optional.ofNullable(bc.getBundle(location));
			}
		}
		return Optional.empty();
	}

	protected final String getBundleLocation(Class<?> classFromBundle) {
		lockRead();
		try {
			return atomosKeyToOSGiLocation.get(getAtomosKey(classFromBundle));
		} finally {
			unlockRead();
		}
	}

	protected Object getAtomosKey(Class<?> classFromBundle) {
		return classFromBundle.getProtectionDomain().getCodeSource().getLocation();
	}

	protected abstract void filterBasedOnReadEdges(AtomosBundleInfo atomosBundle, Collection<BundleCapability> candidates);

	protected final void filterNotVisible(AtomosBundleInfo atomosBundle, Collection<BundleCapability> candidates) {
		if (atomosBundle != null) {
			for (Iterator<BundleCapability> iCands = candidates.iterator(); iCands.hasNext();) {
				BundleCapability candidate = iCands.next();
				if (!isVisible(atomosBundle, candidate)) {
					iCands.remove();
				}
			}
		}
	}

	private final boolean isVisible(AtomosBundleInfo atomosBundle, BundleCapability candidate) {
		AtomosBundleInfo candidateAtomos = getByOSGiLocation(candidate.getRevision().getBundle().getLocation());
		if (candidateAtomos == null) {
			// atomos bundles cannot see normal bundles
			return false;
		} else {
			AtomosLayer thisLayer = atomosBundle.getAtomosLayer();
			return isInLayerHierarchy(thisLayer, candidateAtomos.getAtomosLayer());				
		}
	}

	final boolean isInLayerHierarchy(AtomosLayer thisLayer, AtomosLayer candLayer) {
		if (thisLayer.equals(candLayer)) {
			return true;
		}
		for (AtomosLayer parent : thisLayer.getParents()) {
			if (isInLayerHierarchy(parent, candLayer)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public AtomosLayer addModules(String name, Path path) {
		throw new UnsupportedOperationException("Cannot add module layers when Atomos is not loaded as module.");
	}

	@Override
	abstract public boolean modulesSupported();

	@SuppressWarnings("unchecked")
	public static <T> Set<T> asSet(Set<? extends T> l) {
		return (Set<T>) l;
	}

	Thread saveOnVMExit = new Thread(() -> {
		BundleContext bc = context.get();
		if (bc != null) {
			try {
				new AtomosStorage(this).saveLayers(storeRoot.get(), bc.getBundles());
			} catch (IOException e) {
				throw new RuntimeException("Failed to save atomos runtime.", e);
			}
		}
	});

	protected void start(BundleContext bc) throws BundleException {
		this.context.set(bc);
		Runtime.getRuntime().addShutdownHook(saveOnVMExit);
		AtomosFrameworkUtilHelper.addHelper(this);

		bc.addBundleListener(this);

		AtomosFrameworkHooks hooks = new AtomosFrameworkHooks(this);
		bc.registerService(ResolverHookFactory.class, hooks, null);
		bc.registerService(CollisionHook.class, hooks, null);

		String initialBundleStartLevel = bc.getProperty(AtomosRuntime.ATOMOS_INITIAL_BUNDLE_START_LEVEL);
		if (initialBundleStartLevel != null) {
			// set the default initial bundle startlevel before installing the atomos bundles
			FrameworkStartLevel fwkStartLevel = bc.getBundle().adapt(FrameworkStartLevel.class);
			fwkStartLevel.setInitialBundleStartLevel(Integer.parseInt(initialBundleStartLevel));
		}
		boolean installBundles = Boolean.valueOf(getProperty(bc, AtomosRuntime.ATOMOS_BUNDLE_INSTALL, "true"));
		boolean startBundles = Boolean.valueOf(getProperty(bc, AtomosRuntime.ATOMOS_BUNDLE_START, "true"));
		installAtomosBundles(getBootLayer(), installBundles, startBundles);
		bc.registerService(AtomosRuntime.class, this, null);
		new AtomosCommands(this).register(bc);
	}

	protected void stop(BundleContext bc) throws BundleException {
		this.context.compareAndSet(bc, null);
		try {
			Runtime.getRuntime().removeShutdownHook(saveOnVMExit);
			new AtomosStorage(this).saveLayers(storeRoot.get(), bc.getBundles());
		} catch (IllegalStateException e) {
			// ignore this; happens if the JVM already is in the process of running shutdown hooks
			// in that case we can skip saveLayers call
		} catch (IOException e) {
			throw new BundleException("Failed to save atomos runtime.", e);
		}

		bc.removeBundleListener(this);

		AtomosFrameworkUtilHelper.removeHelper(this);
	}

	private String getProperty(BundleContext bc, String key, String defaultValue) {
		String result = bc.getProperty(key);
		return result == null ? defaultValue : result;
	}

	private void installAtomosBundles(AtomosLayer atomosLayer, boolean installBundles, boolean startBundles) throws BundleException {
		if (installBundles) {
			List<Bundle> bundles = new ArrayList<>();
			for (AtomosBundleInfo atomosBundle : atomosLayer.getAtomosBundles()) {
				if (getBundle(atomosBundle) == null) {
					Bundle b = atomosBundle.install("atomos");
					if (b != null && b.getBundleId() != 0) {
						bundles.add(b);
					}
				}
			}
			if (startBundles) {
				for (Bundle b : bundles) {
					BundleRevision rev = b.adapt(BundleRevision.class);
					if ((rev.getTypes() & BundleRevision.TYPE_FRAGMENT) == 0) {
						b.start();
					}
				}
				for (AtomosLayer child : atomosLayer.getChildren()) {
					installAtomosBundles(child, installBundles, startBundles);
				}
			}
		}
	}
	public void initialize(File storage, Map<String, String> configuration) {
		if (!storeRoot.compareAndSet(null, storage)) {
			throw new IllegalStateException("This AtomosRuntime is already being used by store: " + storeRoot.get());
		}
		try {
			new AtomosStorage(this).loadLayers(storage);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
