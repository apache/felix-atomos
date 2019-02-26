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
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import java.util.jar.JarFile;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.atomos.framework.base.AtomosRuntimeBase.AtomosLayerBase.AtomosBundleInfoBase;
import org.atomos.framework.modules.AtomosRuntimeModules;
import org.eclipse.osgi.container.ModuleRevisionBuilder;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.internal.framework.EquinoxContainer;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.eclipse.osgi.storage.bundlefile.DirBundleFile;
import org.eclipse.osgi.storage.bundlefile.MRUBundleFileList;
import org.eclipse.osgi.storage.url.reference.ReferenceInputStream;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.FrameworkWiring;

public abstract class AtomosRuntimeBase implements AtomosRuntime, SynchronousBundleListener{
	static final String JAR_PROTOCOL = "jar"; //$NON-NLS-1$
	static final String FILE_PROTOCOL = "file"; //$NON-NLS-1$

	private final AtomicReference<AtomosHookConfigurator> configurator = new AtomicReference<>();
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final Map<String, AtomosBundleInfoBase> byOSGiLocation = new HashMap<>();
	private final Map<String, AtomosBundleInfoBase> byAtomosLocation = new HashMap<>();
	final Map<AtomosLayer, Collection<String>> byAtomosLayer = new HashMap<>();
	protected final Map<Object, String> byAtomosKey = new HashMap<>();
	final Map<Long, AtomosLayerBase> byId = new HashMap<>();
	private final Map<AtomosBundleInfo, String> byAtomosBundle = new HashMap<>();
	protected final AtomicLong nextLayerId = new AtomicLong(0);

	public static AtomosRuntimeBase newAtomosRuntime() {
		try {
			Class.forName("java.lang.Module");
			return new AtomosRuntimeModules();
		} catch (ClassNotFoundException e) {
			return new AtomosRuntimeClassPath();
		}
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


	abstract protected AtomosLayer createBootLayer();

	protected final AtomosBundleInfoBase getByOSGiLocation(String location) {
		lockRead();
		try {
			return byOSGiLocation.get(location);
		} finally {
			unlockRead();
		}
	}

	final void addToInstalledBundles(String osgiLocation, AtomosBundleInfoBase atomosBundle) {
		lockWrite();
		try {
			byOSGiLocation.put(osgiLocation, atomosBundle);
			byAtomosBundle.put(atomosBundle, osgiLocation);
			AtomosLayer layer = atomosBundle.getAtomosLayer();
			byAtomosLayer.computeIfAbsent(layer, (l) -> new ArrayList<>()).add(osgiLocation);
			byAtomosKey.put(atomosBundle.getKey(), osgiLocation);
		} finally {
			unlockWrite();
		}
	}

	private void removeFromInstalledBundles(String osgiLocation) {
		lockWrite();
		try {
			AtomosBundleInfoBase removed = byOSGiLocation.remove(osgiLocation);
			byAtomosBundle.remove(removed);
			byAtomosLayer.computeIfAbsent(removed.getAtomosLayer(), (l) -> new ArrayList<>()).remove(osgiLocation);
			byAtomosKey.remove(removed.getKey());
		} finally {
			unlockWrite();
		}
	}

	final AtomosBundleInfoBase getByAtomosLocation(String location) {
		lockRead();
		try {
			return byAtomosLocation.get(location);
		} finally {
			unlockRead();
		}
	}

	protected final AtomosLayerBase getById(long id) {
		lockRead();
		try {
			return byId.get(id);
		} finally {
			unlockRead();
		}
	}

	final String getByAtomosBundleInfo(AtomosBundleInfo atomosBundle) {
		lockRead();
		try {
			return byAtomosBundle.get(atomosBundle);
		} finally {
			unlockRead();
		}
	}

	final Collection<String> getInstalledLocations(AtomosLayer layer) {
		lockRead();
		try {
			return new ArrayList<>(byAtomosLayer.computeIfAbsent(layer, (l) -> new ArrayList<>()));
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
			AtomosHookConfigurator current = configurator.get();
			if (current != null) {
				BundleContext bc = current.getBundleContext();
				if (bc != null) {
					return bc.getBundle(location);
				}
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
		if (configurator.get() != null) {
			throw new IllegalStateException("AtomosRuntime is already be used by another Framework instance.");
		}
		AtomosHookConfigurator.bootAtomRuntime.set(this);
		try {
			return findFrameworkFactory().newFramework(frameworkConfig);
		} finally {
			AtomosHookConfigurator.bootAtomRuntime.set(null);
		}
	}

	abstract protected FrameworkFactory findFrameworkFactory();

	final void setConfigurator(AtomosHookConfigurator prev, AtomosHookConfigurator next) {
		AtomosHookConfigurator result = this.configurator.updateAndGet((p) -> {
			if (p == prev) {
				return next;
			}
			// special handling of null for cases where we are just making sure next is already set
			if (prev == null && p == next) {
				return next;
			}
			return p;
		});
		if (result != next) {
			throw new IllegalStateException("Cannot use the same AtomosRuntime for multiple Frameworks.");
		}

		AtomosFrameworkUtilHelper.atomosRuntime = this;
	}

	final BundleContext getBundleContext() {
		AtomosHookConfigurator current = configurator.get();
		if (current == null) {
			return null;
		}
		return current.getBundleContext();
	}

	final ThreadLocal<AtomosBundleInfo> installingInfo = new ThreadLocal<>();
	final Bundle installAtomosBundle(String prefix, AtomosBundleInfo atomosBundle) throws BundleException {
		if (prefix == null) {
			prefix = "atomos";
		}
		if (AtomosHookConfigurator.DEBUG) {
			System.out.println("Installing atomos bundle: " + prefix + atomosBundle.getLocation()); //$NON-NLS-1$
		}

		AtomosHookConfigurator current = configurator.get();
		if (current == null) {
			throw new IllegalStateException("No framework has been created.");
		}

		if (prefix.indexOf(':') != -1) {
			throw new IllegalArgumentException("The prefix cannot contain ':'");
		}

		BundleContext bc = current.getBundleContext();
		File emptyJar = current.getEmptyJar();
		if (bc == null || emptyJar == null) {
			throw new IllegalStateException("Framework has not been initialized.");
		}
		String location = atomosBundle.getLocation();
		if (!Constants.SYSTEM_BUNDLE_LOCATION.equals(location)) {
			location = prefix + ':' + atomosBundle.getLocation();
		}
		AtomosLayerBase atomosLayer = (AtomosLayerBase) atomosBundle.getAtomosLayer();
		if (!atomosLayer.isValid()) {
			throw new BundleException("Atomos layer has been uninstalled.", BundleException.INVALID_OPERATION);
		}

		addToInstalledBundles(location, (AtomosBundleInfoBase) atomosBundle);
		installingInfo.set(atomosBundle);
		Bundle result = null;
		try {
			result = bc.installBundle(location, new ReferenceInputStream(emptyJar));
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
		if (byId.putIfAbsent(atomosLayer.getId(), atomosLayer) != null) {
			throw new IllegalStateException("AtomosLayer already exists for id: " + atomosLayer.getId());
		}

		for (AtomosBundleInfo atomosBundle : atomosLayer.getAtomosBundles()) {
			if (byAtomosLocation.putIfAbsent(atomosBundle.getLocation(), (AtomosBundleInfoBase) atomosBundle) != null) {
				throw new IllegalStateException("Atomos bundle location already exists: " + atomosBundle.getLocation());
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
					Map<String, String> headers = ManifestElement.parseBundleManifest(manifest.openStream(), null);
					String bsnHeader = headers.get(Constants.BUNDLE_SYMBOLICNAME);
					ManifestElement[] element = ManifestElement.parseHeader(Constants.BUNDLE_SYMBOLICNAME, bsnHeader);
					String symbolicName = element != null ? element[0].getValue() : null;
					if (symbolicName != null) {
						Object content = getBundleContent(manifest);
						if (content != null) {
							String location;
							if (EquinoxContainer.NAME.equals(symbolicName)) {
								location = Constants.SYSTEM_BUNDLE_LOCATION;
							} else {
								location = content instanceof File ? ((File) content).getPath() : ((JarFile) content).getName();
								if (!getName().isEmpty()) {
									location = getName() + ":" + location;
								}
							}
							Version version = Version.parseVersion(headers.get(Constants.BUNDLE_VERSION));
							if (content instanceof File) {
								bootBundles.add(new AtomosBundleInfoDir((File) content, location, symbolicName, version));
							} else {
								bootBundles.add(new AtomosBundleInfoJar((JarFile) content, location, symbolicName, version));
							}
						}
					}
				}
			} catch (IOException | BundleException e) {
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
			byAtomosLayer.remove(this);
			byId.remove(getId());
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

			public AtomosBundleInfoBase(String location, String symbolicName, Version version) {
				this.location = location;
				this.symbolicName = symbolicName;
				this.version = version;
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
				return getSymbolicName().equals(info.getSymbolicName()) && getVersion().equals(info.getVersion());
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

			protected abstract BundleFile getBundleFile(BundleFile bundleFile, Generation generation, MRUBundleFileList mruList, Debug debug) throws IOException;
			protected abstract Object getKey();

			abstract protected URL getContentURL();

			public final String toString() {
				return symbolicName;
			}

			@Override
			public final Bundle install(String prefix) throws BundleException {
				return installAtomosBundle(prefix, this);
			}
		}

		public class AtomosBundleInfoDir extends AtomosBundleInfoBase {

			private final File dir;
			private final URL contentURL;


			public AtomosBundleInfoDir(File dir, String location, String symbolicName, Version version) {
				super(location, symbolicName, version);
				this.dir = dir;
				try {
					this.contentURL = dir.toURI().toURL();
				} catch (MalformedURLException e) {
					throw new IllegalArgumentException(e);
				}
			}

			@Override
			protected final BundleFile getBundleFile(BundleFile bundleFile, Generation generation, MRUBundleFileList mruList, Debug debug) throws IOException {
				return new DirBundleFile(dir, true);
			}

			@Override
			protected final URL getContentURL() {
				return contentURL;
			}

			@Override
			protected final Object getKey() {
				return getContentURL();
			}
		}

		public class AtomosBundleInfoJar extends AtomosBundleInfoBase {

			private final JarFile jarFile;
			private final URL contentURL;


			public AtomosBundleInfoJar(JarFile jarFile, String location, String symbolicName, Version version) {
				super(location, symbolicName, version);
				this.jarFile = jarFile;
				try {
					this.contentURL = new File(jarFile.getName()).toURI().toURL();
				} catch (MalformedURLException e) {
					throw new IllegalArgumentException(e);
				}
			}

			@Override
			protected final BundleFile getBundleFile(BundleFile bundleFile, Generation generation, MRUBundleFileList mruList, Debug debug) throws IOException {
				return new JarBundleFile(jarFile, bundleFile.getBaseFile(), generation, debug);
			}

			@Override
			protected final URL getContentURL() {
				return contentURL;
			}

			@Override
			protected final Object getKey() {
				return getContentURL();
			}
		}
	}

	final Bundle getBundle(Class<?> classFromBundle) {
		String location = getBundleLocation(classFromBundle);
		if (location != null) {
			AtomosHookConfigurator current = configurator.get();
			if (current != null) {
				BundleContext bc = current.bc;
				if (bc != null) {
					return bc.getBundle(location);
				}
			}
		}
		return null;
	}

	protected String getBundleLocation(Class<?> classFromBundle) {
		lockRead();
		try {
			return byAtomosKey.get(getCodePath(classFromBundle));
		} finally {
			unlockRead();
		}
	}

	private URL getCodePath(Class<?> classFromBundle) {
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

	abstract protected ClassLoader getClassLoader(AtomosBundleInfoBase atomosBundle);
	
	abstract protected ModuleRevisionBuilder createBuilder(AtomosBundleInfoBase atomosBundle, ModuleRevisionBuilder original,
			HookRegistry hookRegistry);

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
}
