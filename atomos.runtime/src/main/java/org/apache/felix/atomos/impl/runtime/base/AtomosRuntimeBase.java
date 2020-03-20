/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.atomos.impl.runtime.base;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase.AtomosLayerBase.AtomosContentBase;
import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase.AtomosLayerBase.AtomosContentIndexed;
import org.apache.felix.atomos.impl.runtime.content.ConnectContentCloseableJar;
import org.apache.felix.atomos.impl.runtime.content.ConnectContentFile;
import org.apache.felix.atomos.impl.runtime.content.ConnectContentIndexed;
import org.apache.felix.atomos.impl.runtime.content.ConnectContentJar;
import org.apache.felix.atomos.runtime.AtomosContent;
import org.apache.felix.atomos.runtime.AtomosLayer;
import org.apache.felix.atomos.runtime.AtomosLayer.LoaderType;
import org.apache.felix.atomos.runtime.AtomosRuntime;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.connect.FrameworkUtilHelper;
import org.osgi.framework.connect.ModuleConnector;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.FrameworkWiring;

import sun.misc.Signal;

public abstract class AtomosRuntimeBase implements AtomosRuntime, SynchronousBundleListener, FrameworkUtilHelper, FrameworkListener
{
    static final String JAR_PROTOCOL = "jar";
    static final String FILE_PROTOCOL = "file";
    public static final String ATOMOS_PROP_PREFIX = "atomos.";
    public static final String ATOMOS_REPORT_RESOLUTION_PROP = ATOMOS_PROP_PREFIX
        + "enable.resolution.errors";
    public static final String ATOMOS_DEBUG_PROP = ATOMOS_PROP_PREFIX + "enable.debug";
    public static final String ATOMOS_INDEX_PATH_PROP = ATOMOS_PROP_PREFIX + "index.path";
    public static final String ATOMOS_IGNORE_INDEX = "IGNORE";
    public static final String ATOMOS_BUNDLES_INDEX_DEFAULT = "/atomos/bundles.index";
    public static final String ATOMOS_BUNDLE = "ATOMOS_BUNDLE";
    public static final String ATOMOS_LIB_DIR_PROP = ATOMOS_PROP_PREFIX + "lib.dir";
    public static final String ATOMOS_RUNTIME_CLASS_PROP = ATOMOS_PROP_PREFIX
        + "runtime.class";
    public static final String ATOMOS_RUNTIME_MODULES_CLASS = "org.apache.felix.atomos.impl.runtime.modules.AtomosRuntimeModules";
    public static final String ATOMOS_LIB_DIR = "atomos_lib";
    public static final String GRAAL_NATIVE_IMAGE_KIND = "org.graalvm.nativeimage.kind";

    private final boolean DEBUG;
    private final boolean REPORT_RESOLUTION_ERRORS;
    private final String indexPath;

    private final AtomicReference<BundleContext> context = new AtomicReference<>();
    private final AtomicReference<File> storeRoot = new AtomicReference<>();

    private ServiceRegistration<?> atomosCommandsReg = null;
    private ServiceRegistration<?> atomosRuntimeReg = null;
    protected final Map<String, String> config = new ConcurrentHashMap<String, String>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // The following are all protected by the read/write lock

    // A map of Atomos contents that have a connect location; the key is the connect location
    private final Map<String, AtomosContentBase> connectLocationToAtomosContent = new HashMap<>();
    // A map of all Atomos contents discovered (may not be installed as OSGi bundles); the key is the Atomos location
    private final Map<String, AtomosContentBase> atomosLocationToAtomosContent = new HashMap<>();
    // A map of connect locations for Atomos contents; key is the AtomosContentBase.getKey()
    // Used to lookup an OSGi bundle location for a Class<?> in getBundleLocation(Class<?>)
    protected final Map<Object, String> atomosKeyToConnectLocation = new HashMap<>();
    // A map of Layers keyed by layer ID
    final Map<Long, AtomosLayerBase> idToLayer = new HashMap<>();
    // A map of connect locations for Atomos contents; key is Atomos content
    private final Map<AtomosContent, String> atomosContentToConnectLocation = new HashMap<>();
    // A set of connect locations that the framework has connected using the AtomosModuleConnector
    private final Map<String, AtomosContentBase> connectedLocations = new HashMap<>();
    // A map of indexed content; key is package name
    private final Map<String, AtomosContentIndexed> packageToAtomosContent = new ConcurrentHashMap<>();

    protected final AtomicLong nextLayerId = new AtomicLong(0);

    public static enum Index
    {
        IGNORE, FIRST
    }

    public static AtomosRuntime newAtomosRuntime(Map<String, String> config)
    {
        String runtimeClass = config.get(ATOMOS_RUNTIME_CLASS_PROP);
        if (runtimeClass != null)
        {
            return loadRuntime(runtimeClass, config);
        }
        if (config.get(
            ATOMOS_LIB_DIR_PROP) != null
            || System.getProperty(GRAAL_NATIVE_IMAGE_KIND) != null)
        {
            return new AtomosRuntimeClassPath(config);
        }
        try
        {
            Class.forName("java.lang.Module");
            return loadRuntime(ATOMOS_RUNTIME_MODULES_CLASS, config);
        }
        catch (ClassNotFoundException e)
        {
            // ignore
        }
        // default to classpath
        return new AtomosRuntimeClassPath(config);
    }

    private static AtomosRuntime loadRuntime(String runtimeClass,
        Map<String, String> config)
    {
        try
        {
            return (AtomosRuntimeBase) Class.forName(
                runtimeClass).getConstructor(Map.class).newInstance(config);
        }
        catch (Exception e)
        {
            throw e instanceof RuntimeException ? (RuntimeException) e
                : new RuntimeException(e);
        }
    }

    public File findAtomosLibDir()
    {
        String libDirProp = config.get(ATOMOS_LIB_DIR_PROP);
        return new File(libDirProp, ATOMOS_LIB_DIR);
    }

    protected AtomosRuntimeBase(Map<String, String> config)
    {
        saveConfig(config);
        DEBUG = Boolean.parseBoolean(this.config.get(ATOMOS_DEBUG_PROP));
        REPORT_RESOLUTION_ERRORS = Boolean.parseBoolean(
            this.config.get(ATOMOS_REPORT_RESOLUTION_PROP));
        indexPath = getIndexPath(this.config.get(ATOMOS_INDEX_PATH_PROP));

        try
        {
            // substrate native image does not run shutdown hooks on ctrl-c
            // this works around it by using our own signal handler
            Signal.handle(new Signal("INT"), sig -> System.exit(0));
        }
        catch (Throwable t)
        {
            // do nothing if Signal isn't available
        }
    }

    private String getIndexPath(String indexPath)
    {
        if (indexPath == null)
        {
            indexPath = ATOMOS_BUNDLES_INDEX_DEFAULT;
        }
        else if (!ATOMOS_IGNORE_INDEX.equals(indexPath) && !indexPath.startsWith("/"))
        {
            indexPath = "/" + indexPath;
        }
        return indexPath;
    }

    protected final void lockWrite()
    {
        lock.writeLock().lock();
    }

    protected final void unlockWrite()
    {
        lock.writeLock().unlock();
    }

    protected final void lockRead()
    {
        lock.readLock().lock();
    }

    protected final void unlockRead()
    {
        lock.readLock().unlock();
    }

    protected final AtomosContentBase getByConnectLocation(String location, boolean isManaged)
    {
        lockRead();
        try
        {
            AtomosContentBase result = null;
            if (isManaged && !Constants.SYSTEM_BUNDLE_LOCATION.equals(location))
            {
                result = connectedLocations.get(location);
            }
            else
            {
                result = connectLocationToAtomosContent.get(location);
            }
            debug("Found content %s for location: %s %s", result, location, isManaged);
            return result;
        }
        finally
        {
            unlockRead();
        }
    }

    final void connectAtomosContent(
        String connectLocation,
        AtomosContentBase atomosContent)
    {
        debug("Connecting content: %s %s", atomosContent, connectLocation);
        if (connectLocation == null)
        {
            throw new IllegalArgumentException("A null connect loation is not allowed.");
        }
        lockWrite();
        try
        {
            AtomosContent existing = connectLocationToAtomosContent.get(connectLocation);
            if (existing != null && !atomosContent.equals(existing))
            {
                throw new IllegalStateException(
                    "The bundle location is already used by the AtomosContent "
                        + existing);
            }
            String computeLocation = atomosContentToConnectLocation.compute(atomosContent,
                (c, l) -> l == null ? connectLocation : l);

            if (!Objects.equals(connectLocation, computeLocation))
            {
                throw new IllegalStateException(
                    "Atomos content location is already set: " + computeLocation);
            }
            connectLocationToAtomosContent.put(connectLocation, atomosContent);
            atomosKeyToConnectLocation.put(atomosContent.getKey(), connectLocation);
        }
        finally
        {
            unlockWrite();
        }
    }

    void disconnectAtomosContent(AtomosContentBase atomosContent)
    {
        debug("Disconnecting connent: %s", atomosContent);
        lockWrite();
        try
        {
            if (Constants.SYSTEM_BUNDLE_LOCATION.equals(
                atomosContent.getAtomosLocation()))
            {
                throw new UnsupportedOperationException(
                    "Cannot disconnect the system bundle content");
            }
            String removedLocation = atomosContentToConnectLocation.remove(atomosContent);
            if (removedLocation != null)
            {
                debug("Disconnecting location: %s %s", removedLocation, atomosContent);
                connectLocationToAtomosContent.remove(removedLocation);
                atomosKeyToConnectLocation.remove(atomosContent.getKey());
                connectedLocations.remove(removedLocation);
            }
            else
            {
                debug("No connected location found for content: %s", atomosContent);
            }
        }
        finally
        {
            unlockWrite();
        }
    }

    final AtomosContentBase getByAtomosLocation(String location)
    {
        lockRead();
        try
        {
            return atomosLocationToAtomosContent.get(location);
        }
        finally
        {
            unlockRead();
        }
    }

    protected final AtomosLayerBase getById(long id)
    {
        lockRead();
        try
        {
            return idToLayer.get(id);
        }
        finally
        {
            unlockRead();
        }
    }

    final String getByAtomosContent(AtomosContent atomosContent)
    {
        lockRead();
        try
        {
            return atomosContentToConnectLocation.get(atomosContent);
        }
        finally
        {
            unlockRead();
        }
    }

    @Override
    public final AtomosContent getConnectedContent(String bundleLocation)
    {
        return getByConnectLocation(bundleLocation, true);
    }

    final Bundle getBundle(AtomosContent atomosContent)
    {
        String location = getByAtomosContent(atomosContent);
        if (location != null)
        {
            if (atomosContent == getByConnectLocation(location, true))
            {
                BundleContext bc = context.get();
                if (bc != null)
                {
                    return bc.getBundle(location);
                }
            }
        }
        return null;
    }

    abstract protected AtomosLayer addLayer(List<AtomosLayer> parents, String name,
        long id, LoaderType loaderType, Path... paths);


    @Override
    public ModuleConnector getModuleConnector()
    {
        return new AtomosModuleConnector(this);
    }

    abstract public ConnectFrameworkFactory findFrameworkFactory();

    protected final BundleContext getBundleContext()
    {
        return context.get();
    }

    final ThreadLocal<Deque<AtomosContent>> managingConnected = ThreadLocal.withInitial(ArrayDeque::new);

    final Bundle installAtomosContent(String prefix,
        AtomosContentBase atomosContent)
        throws BundleException
    {
        if (prefix == null)
        {
            prefix = "atomos";
        }
        if (prefix.indexOf(':') != -1)
        {
            throw new IllegalArgumentException("The prefix cannot contain ':'");
        }
        prefix = prefix + ':';
        debug("Installing atomos content: %s%s", prefix,
            atomosContent.getAtomosLocation());

        BundleContext bc = context.get();
        if (bc == null)
        {
            throw new IllegalStateException("Framework has not been initialized.");
        }

        String location = atomosContent.getAtomosLocation();
        if (!Constants.SYSTEM_BUNDLE_LOCATION.equals(location))
        {
            location = prefix + location;
        }

        AtomosLayerBase atomosLayer = (AtomosLayerBase) atomosContent.getAtomosLayer();
        if (atomosLayer.isNotValid())
        {
            throw new BundleException("Atomos layer has been uninstalled.",
                BundleException.INVALID_OPERATION);
        }

        String existingLoc = getByAtomosContent(atomosContent);
        if (existingLoc != null)
        {
            Bundle existing = bc.getBundle(existingLoc);
            if (existing != null)
            {
                if (Constants.SYSTEM_BUNDLE_LOCATION.equals(existingLoc)
                    || (existingLoc.equals(location)
                        && atomosContent.getBundle() == existing))
                {
                    return existing;
                }
                throw new BundleException(
                    "Atomos content is already connected with bundle: "
                        + existing,
                    BundleException.DUPLICATE_BUNDLE_ERROR);
            }
        }

        atomosContent.disconnect();
        atomosContent.connect(location);

        Bundle result = null;
        try
        {
            result = bc.installBundle(location);
        }
        finally
        {
            // check if the layer is still valid
            if (atomosLayer.isNotValid())
            {
                // The atomosLayer became invalid while installing
                if (result != null)
                {
                    result.uninstall();
                    result = null;
                }
            }
        }
        return result;
    }

    final AtomosContent currentlyManagingConnected()
    {
        return managingConnected.get().peekLast();
    }

    final void addManagingConnected(AtomosContentBase atomosBundle, String location)
    {
        lockWrite();
        try
        {
            connectedLocations.compute(location, (l, a) -> {
                if (a == null || a == atomosBundle)
                {
                    return atomosBundle;
                }
                throw new IllegalStateException(
                    "Atomos connect location is already managed by: " + a);
            });
        }
        finally
        {
            unlockWrite();
        }
        if (context.get() != null)
        {
            managingConnected.get().addLast(atomosBundle);
        }
    }

    @Override
    public final void bundleChanged(BundleEvent event)
    {
        boolean connectionManaged = true;
        String location = event.getBundle().getLocation();
        switch (event.getType())
        {
            case BundleEvent.INSTALLED:
            case BundleEvent.UPDATED :
                addPackages(event.getBundle());
                AtomosContent content = getByConnectLocation(location, true);
                if (content != null)
                {
                    debug("Bundle successfully connected %s", content);
                    connectionManaged = managingConnected.get().removeLastOccurrence(
                        content);
                }
                else 
                {
                    connectionManaged = false;
                }
                break;
            case BundleEvent.UNINSTALLED :
                connectionManaged = false;
                break;
            default:
                break;
        }

        if (!connectionManaged)
        {
            lockWrite();
            try
            {
                debug("Removing location %s as a connected location.", location);
                connectedLocations.remove(location);
            }
            finally
            {
                unlockWrite();
            }
        }
    }

    @Override
    public void frameworkEvent(FrameworkEvent event)
    {
        if (REPORT_RESOLUTION_ERRORS && event.getType() == FrameworkEvent.ERROR)
        {
            if (event.getThrowable() instanceof BundleException
                && ((BundleException) event.getThrowable()).getType() == BundleException.RESOLVE_ERROR)
            {
                Bundle b = event.getBundle();
                BundleRevision rev = b == null ? null : b.adapt(BundleRevision.class);
                if (rev != null)
                {
                    rev.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE).forEach(
                        i -> {
                            @SuppressWarnings("unchecked")
                            List<String> tags = (List<String>) i.getAttributes().get(
                                IdentityNamespace.CAPABILITY_TAGS_ATTRIBUTE);
                            if (tags != null
                                && tags.contains(ConnectContent.TAG_OSGI_CONNECT))
                            {
                                System.out.println("Unable to resolve connected bundle: "
                                    + event.getThrowable().getMessage());
                            }
                        });
                }
            }
        }
    }

    protected final void addAtomosLayer(AtomosLayerBase atomosLayer)
    {
        addingLayer(atomosLayer);
        if (idToLayer.putIfAbsent(atomosLayer.getId(), atomosLayer) != null)
        {
            throw new IllegalStateException(
                "AtomosLayer already exists for id: " + atomosLayer.getId());
        }

        for (AtomosContent atomosContent : atomosLayer.getAtomosContents())
        {
            if (atomosLocationToAtomosContent.putIfAbsent(
                atomosContent.getAtomosLocation(), (AtomosContentBase) atomosContent) != null)
            {
                throw new IllegalStateException("Atomos content location already exists: "
                    + atomosContent.getAtomosLocation());
            }

            if (Constants.SYSTEM_BUNDLE_LOCATION.equals(atomosContent.getAtomosLocation()))
            {
                // system bundle location is always marked as connected
                connectAtomosContent(Constants.SYSTEM_BUNDLE_LOCATION,
                    (AtomosContentBase) atomosContent);
            }
        }
        for (AtomosLayer parent : atomosLayer.getParents())
        {
            ((AtomosLayerBase) parent).addChild(atomosLayer);
        }
    }

    abstract protected void addingLayer(AtomosLayerBase atomosLayer);

    abstract protected void removedLayer(AtomosLayerBase atomosLayer);

    abstract public class AtomosLayerBase implements AtomosLayer
    {
        private final long id;
        private final String name;
        private final LoaderType loaderType;
        private final List<AtomosLayer> parents;
        private final Set<AtomosLayer> children = new HashSet<>();
        private final List<Path> paths;
        private volatile boolean valid = true;
        private volatile Map<String, AtomosContent> nameToBundle;

        public AtomosLayerBase(List<AtomosLayer> parents, long id, String name, LoaderType loaderType, Path... paths)
        {
            this.id = id;
            this.name = name == null ? "" : name;
            this.paths = Arrays.asList(paths);
            this.parents = parents;
            this.loaderType = loaderType;
        }

        @Override
        public AtomosLayer addLayer(String name, LoaderType loaderType,
            Path... modulePaths)
        {
            return AtomosRuntimeBase.this.addLayer(Collections.singletonList(this), name,
                -1, loaderType, modulePaths);
        }

        @Override
        public AtomosLayer addModules(String name, Path path)
        {
            throw new UnsupportedOperationException(
                "Cannot add module layers when Atomos is not loaded as module.");
        }

        @Override
        public boolean isAddLayerSupported()
        {
            return false;
        }

        protected final void addChild(AtomosLayerBase child)
        {
            children.add(child);
        }

        protected final void removeChild(AtomosLayerBase child)
        {
            children.remove(child);
        }

        protected final Set<AtomosContentBase> findAtomosContents()
        {
            Set<AtomosContentBase> bootBundles = new HashSet<>();

            // first get the modules from the boot ModuleLayer (Java 9+ JPMS)
            findBootModuleLayerAtomosContents(bootBundles);
            // for class path mode we look for manifests on the classpath
            findAtomosContentsByClassLoaderManifests(bootBundles);
            // finally find by Atomos index, note that Atomos index content will override duplicates
            findAtomosIndexedContents(bootBundles);

            return Collections.unmodifiableSet(bootBundles);
        }

        protected abstract void findBootModuleLayerAtomosContents(
            Set<AtomosContentBase> result);

        void findAtomosContentsByClassLoaderManifests(Set<AtomosContentBase> result)
        {
            try
            {
                ClassLoader cl = getClass().getClassLoader();
                Set<URL> parentManifests = new HashSet<>();
                if (cl.getParent() != null)
                {
                    Enumeration<URL> eParentManifests = cl.getParent().getResources(
                        JarFile.MANIFEST_NAME);
                    while (eParentManifests.hasMoreElements())
                    {
                        parentManifests.add(eParentManifests.nextElement());
                    }
                }
                Enumeration<URL> classpathManifests = cl.getResources(
                    JarFile.MANIFEST_NAME);
                while (classpathManifests.hasMoreElements())
                {
                    URL manifest = classpathManifests.nextElement();
                    if (parentManifests.contains(manifest))
                    {
                        // ignore parent manifests
                        continue;
                    }
                    Attributes headers = new Manifest(
                        manifest.openStream()).getMainAttributes();
                    String symbolicName = headers.getValue(Constants.BUNDLE_SYMBOLICNAME);
                    if (symbolicName != null)
                    {
                        int semiColon = symbolicName.indexOf(';');
                        if (semiColon != -1)
                        {
                            symbolicName = symbolicName.substring(0, semiColon);
                        }
                        symbolicName = symbolicName.trim();

                        Object content = getBundleContent(manifest);
                        if (content != null)
                        {
                            ConnectContent connectContent;
                            URL url;
                            if (content instanceof File)
                            {
                                connectContent = new ConnectContentFile((File) content);
                                url = ((File) content).toURI().toURL();

                            }
                            else
                            {
                                connectContent = new ConnectContentJar(
                                    () -> ((JarFile) content), null);
                                url = new File(
                                    ((JarFile) content).getName()).toURI().toURL();
                            }

                            String location;
                            if (connectContent.getEntry(
                                "META-INF/services/org.osgi.framework.launch.FrameworkFactory").isPresent())
                            {
                                location = Constants.SYSTEM_BUNDLE_LOCATION;
                            }
                            else
                            {
                                location = content instanceof File
                                    ? ((File) content).getPath()
                                    : ((JarFile) content).getName();
                                if (!getName().isEmpty())
                                {
                                    location = getName() + ":" + location;
                                }
                            }
                            Version version = Version.parseVersion(
                                headers.getValue(Constants.BUNDLE_VERSION));

                            result.add(new AtomosContentClassPath(location, symbolicName,
                                version, connectContent, url));
                        }
                    }
                }
            }
            catch (IOException e)
            {
                throw new IllegalStateException("Error finding class path bundles.", e);
            }
        }

        private void findAtomosIndexedContents(Set<AtomosContentBase> bootBundles)
        {
            URL index = ATOMOS_IGNORE_INDEX.equals(indexPath)
                ? null
                : getClass().getResource(indexPath);
            debug("Atomos index url: %s", index);
            if (index != null)
            {
                findAtomosIndexedContent(index, bootBundles);
            }
            else
            {
                File atomosLibDir = findAtomosLibDir();
                if (atomosLibDir.isDirectory())
                {
                    findAtomosLibIndexedContent(bootBundles, atomosLibDir);
                }
            }
        }

        private void findAtomosLibIndexedContent(Set<AtomosContentBase> bootBundles,
            File atomosLibDir)
        {
            for (File f : atomosLibDir.listFiles())
            {
                if (f.isFile())
                {
                    try (JarFile jar = new JarFile(f))
                    {
                        Attributes headers = jar.getManifest().getMainAttributes();
                        String symbolicName = headers.getValue(
                            Constants.BUNDLE_SYMBOLICNAME);
                        if (symbolicName != null)
                        {
                            int semiColon = symbolicName.indexOf(';');
                            if (semiColon != -1)
                            {
                                symbolicName = symbolicName.substring(0, semiColon);
                            }
                            symbolicName = symbolicName.trim();

                            ConnectContent connectContent = new ConnectContentCloseableJar(
                                f.getName(), () -> atomosLibDir);
                            connectContent.open();
                            String location;
                            if (connectContent.getEntry(
                                "META-INF/services/org.osgi.framework.launch.FrameworkFactory").isPresent())
                            {
                                location = Constants.SYSTEM_BUNDLE_LOCATION;
                            }
                            else
                            {
                                location = f.getName();
                                if (!getName().isEmpty())
                                {
                                    location = getName() + ":" + location;
                                }
                            }
                            Version version = Version.parseVersion(
                                headers.getValue(Constants.BUNDLE_VERSION));
                            AtomosContentBase bundle = new AtomosContentIndexed(location,
                                symbolicName, version, connectContent);
                            bootBundles.add(bundle);
                        }
                    }
                    catch (IOException e)
                    {
                        // ignore and continue
                    }
                }
            }
        }

        private AtomosContentIndexed createIndexedContent(String indexRoot,
            String currentIndex,
            String currentBSN, Version currentVersion, List<String> currentPaths)
        {
            String bundleIndexPath = indexRoot + currentIndex;
            ConnectContentIndexed content = new ConnectContentIndexed(bundleIndexPath,
                currentPaths);
            debug("Found indexed content: %s %s %s %s", currentIndex, currentBSN,
                currentVersion, currentPaths);
            return new AtomosContentIndexed(getIndexedLocation(content, currentBSN),
                currentBSN, currentVersion, content);
        }

        private void findAtomosIndexedContent(URL index,
            Set<AtomosContentBase> bootBundles)
        {
            final String indexRoot = indexPath.substring(0,
                indexPath.lastIndexOf('/') + 1);
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(index.openStream())))
            {
                String currentIndex = null;
                String currentBSN = null;
                Version currentVersion = null;
                List<String> currentPaths = null;
                String line = reader.readLine();
                while (ATOMOS_BUNDLE.equals(line))
                {
                    if (currentIndex != null)
                    {
                        bootBundles.add(createIndexedContent(indexRoot, currentIndex,
                            currentBSN, currentVersion, currentPaths));
                    }
                    currentIndex = null;
                    currentBSN = null;
                    currentVersion = null;
                    currentPaths = new ArrayList<>();
                    while ((line = reader.readLine()) != null
                        && !ATOMOS_BUNDLE.equals(line))
                    {
                        if (currentIndex == null)
                        {
                            currentIndex = line;
                        }
                        else if (currentBSN == null)
                        {
                            currentBSN = line;
                        }
                        else if (currentVersion == null)
                        {
                            currentVersion = Version.valueOf(line);
                        }
                        else
                        {
                            currentPaths.add(line);
                        }
                    }
                }
                if (currentIndex != null)
                {
                    bootBundles.add(createIndexedContent(indexRoot, currentIndex,
                        currentBSN, currentVersion, currentPaths));
                }
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        private static final String FWK_FACTORY_SERVICE = "META-INF/services/org.osgi.framework.launch.FrameworkFactory";

        private String getIndexedLocation(ConnectContent content, String location)
        {
            return content.getEntry(FWK_FACTORY_SERVICE).map(
                e -> Constants.SYSTEM_BUNDLE_LOCATION).orElseGet(() -> {
                    String qualified = location;
                    ;
                    if (!getName().isEmpty())
                    {
                        qualified = getName() + ":" + location;
                    }
                    return qualified;
                });
        }
        /**
         * Returns the bundle content that contains the specified manifest URL.
         * The return type will be a JarFile or a File for an exploded bundle.
         * @param manifest the manifest URL to get the bundle content for
         * @return a JarFile or File
         */
        private Object getBundleContent(URL manifest)
        {
            if (JAR_PROTOCOL.equals(manifest.getProtocol()))
            {
                // Use a connection to get the JarFile this avoids having to parse the jar: URL
                // For spring loader they support nested jars with additional !/
                // For example: 
                //   jar:file:/path/to/out.jar!/path/to/inner.jar!/META-INF/MANIFEST.MF
                // Instead of dealing with that just get the JarFile directly that supports this
                // embedded jar stuff
                try
                {
                    URLConnection conn = manifest.openConnection();
                    if (conn instanceof JarURLConnection)
                    {
                        return ((JarURLConnection) conn).getJarFile();
                    }
                }
                catch (IOException e)
                {
                    // TODO log?
                }
                // TODO either log or add tracing to help debug issues
            }
            else if (FILE_PROTOCOL.equals(manifest.getProtocol()))
            {
                try
                {
                    File f = new File(manifest.toURI());
                    // return two parents up from the manifest file
                    return f.getParentFile().getParentFile();
                }
                catch (URISyntaxException e)
                {
                    throw new RuntimeException(e);
                }
            }
            return null;
        }

        @Override
        public final String getName()
        {
            return name;
        }

        @Override
        public final Set<AtomosLayer> getChildren()
        {
            lockRead();
            try
            {
                return new HashSet<>(children);
            }
            finally
            {
                unlockRead();
            }
        }

        @Override
        public final List<AtomosLayer> getParents()
        {
            return parents;
        }

        final List<Path> getPaths()
        {
            return paths;
        }

        @Override
        public final long getId()
        {
            return id;
        }

        @Override
        public final LoaderType getLoaderType()
        {
            return loaderType;
        }

        @Override
        public final void uninstall() throws BundleException
        {
            List<Bundle> uninstalledBundles = new ArrayList<>();
            BundleContext bc = getBundleContext();
            if (bc != null)
            {
                uninstallLayer(uninstalledBundles);
            }

            lockWrite();
            try
            {
                // now remove the layer from the runtime
                removeLayerFromRuntime();
            }
            finally
            {
                unlockWrite();
            }

            if (bc != null)
            {
                // now refresh any uninstalled bundles
                bc.getBundle(Constants.SYSTEM_BUNDLE_LOCATION).adapt(
                    FrameworkWiring.class).refreshBundles(uninstalledBundles);
            }
        }

        private void removeLayerFromRuntime()
        {
            for (AtomosLayer parent : getParents())
            {
                ((AtomosLayerBase) parent).removeChild(this);
            }
            for (AtomosLayer child : getChildren())
            {
                ((AtomosLayerBase) child).removeLayerFromRuntime();
            }
            getAtomosContents().forEach(AtomosContent::disconnect);
            idToLayer.remove(getId());
            removedLayer(this);
        }

        final void uninstallLayer(List<Bundle> uninstalledBundles)
            throws BundleException
        {
            // mark as invalid first to prevent installs
            valid = false;
            if (getBootLayer().equals(this))
            {
                throw new UnsupportedOperationException(
                    "Cannot uninstall the boot layer.");
            }
            // first uninstall all children
            for (AtomosLayer child : getChildren())
            {
                ((AtomosLayerBase) child).uninstallLayer(uninstalledBundles);
            }
            uninstallBundles(uninstalledBundles);
        }

        final boolean isNotValid()
        {
            return !valid;
        }

        private void uninstallBundles(List<Bundle> uninstalled)
            throws BundleException
        {
            for (AtomosContent content : getAtomosContents())
            {
                Bundle b = content.getBundle();
                if (b != null)
                {
                    uninstalled.add(b);
                    b.uninstall();
                }
            }
        }

        @Override
        public final String toString()
        {
            StringBuilder result = new StringBuilder();
            result.append('[').append(getId()).append(']');
            result.append(' ').append(getName());
            result.append(' ').append(getLoaderType());
            List<AtomosLayer> parents = getParents();
            if (!parents.isEmpty())
            {
                result.append(" PARENTS: {");
                for (AtomosLayer parent : parents)
                {
                    result.append("[").append(parent.getId()).append(']');
                    result.append(' ').append(parent.getName()).append(", ");
                }
                result.delete(result.length() - 2, result.length());
                result.append("}");
            }
            Set<AtomosLayer> children = getChildren();
            if (!children.isEmpty())
            {
                result.append(" CHILDREN: {");
                for (AtomosLayer child : getChildren())
                {
                    result.append("[").append(child.getId()).append(']');
                    result.append(' ').append(child.getName()).append(", ");
                }
                result.delete(result.length() - 2, result.length());
                result.append("}");
            }
            return result.toString();
        }

        @Override
        public <T> Optional<T> adapt(Class<T> type)
        {
            // do nothing by default
            return Optional.empty();
        }

        @Override
        public Optional<AtomosContent> findAtomosContent(String symbolicName)
        {
            Map<String, AtomosContent> nameToBundle = this.nameToBundle;
            if (nameToBundle == null)
            {
                nameToBundle = new HashMap<>();
                final Map<String, AtomosContent> populate = nameToBundle;
                getAtomosContents().forEach(
                    (a) -> populate.putIfAbsent(a.getSymbolicName(), a));

                Set<AtomosLayer> visited = new HashSet<>();
                Deque<AtomosLayer> stack = new ArrayDeque<>();
                visited.add(this);
                stack.push(this);

                while (!stack.isEmpty())
                {
                    AtomosLayer layer = stack.pop();
                    layer.getAtomosContents().forEach(
                        (a) -> populate.putIfAbsent(a.getSymbolicName(), a));
                    List<AtomosLayer> parents = layer.getParents();
                    for (int i = parents.size() - 1; i >= 0; i--)
                    {
                        AtomosLayer parent = parents.get(i);
                        if (!visited.contains(parent))
                        {
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
         * The base Atomos content implementation that all AtomosContent implementations extend
         */
        public abstract class AtomosContentBase implements AtomosContent, Comparable<AtomosContent>
        {

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

            public AtomosContentBase(String location, String symbolicName, Version version, ConnectContent content)
            {
                this.location = location;
                this.symbolicName = symbolicName;
                this.version = version;
                this.content = content;
            }

            @Override
            public final String getAtomosLocation()
            {
                return location;
            }

            @Override
            public final String getSymbolicName()
            {
                return symbolicName;
            }

            @Override
            public final Version getVersion()
            {
                return version;
            }

            @Override
            public <T> Optional<T> adapt(Class<T> type)
            {
                return Optional.empty();
            }

            @Override
            public final AtomosLayer getAtomosLayer()
            {
                return AtomosLayerBase.this;
            }

            @Override
            public final boolean equals(Object o)
            {
                if (!(o instanceof AtomosContentBase))
                {
                    return false;
                }
                AtomosContentBase info = (AtomosContentBase) o;
                return getSymbolicName().equals(info.getSymbolicName())
                    && getVersion().equals(info.getVersion())
                    && getAtomosLayer() == info.getAtomosLayer();
            }

            @Override
            public final int hashCode()
            {
                return getSymbolicName().hashCode() ^ getVersion().hashCode();
            }

            @Override
            public final int compareTo(AtomosContent o)
            {
                int bsnCompare = getSymbolicName().compareTo(o.getSymbolicName());
                if (bsnCompare != 0)
                {
                    return bsnCompare;
                }
                int vCompare = -(getVersion().compareTo(o.getVersion()));
                if (vCompare != 0)
                {
                    return vCompare;
                }
                return getAtomosLocation().compareTo(o.getAtomosLocation());
            }

            protected abstract Object getKey();

            @Override
            public ConnectContent getConnectContent()
            {
                debug("Getting connect content for %s", this);
                return content;
            }

            public final String toString()
            {
                return symbolicName;
            }

            @Override
            public final Bundle install(String prefix) throws BundleException
            {
                return installAtomosContent(prefix, this);
            }

            @Override
            public Bundle getBundle()
            {
                return AtomosRuntimeBase.this.getBundle(this);
            }

            @Override
            public String getConnectLocation()
            {
                return getByAtomosContent(this);
            }

            @Override
            public void connect(String bundleLocation)
            {
                connectAtomosContent(bundleLocation, this);
            }

            @Override
            public void disconnect()
            {
                disconnectAtomosContent(this);
            }
        }

        /**
         * Atomos content discovered on the class path.  The key is the a file URL
         * to the file on disk which is on the class path.
         *
         */
        public class AtomosContentClassPath extends AtomosContentBase
        {

            private final URL contentURL;

            public AtomosContentClassPath(String location, String symbolicName, Version version, ConnectContent connectContent, URL url)
            {
                super(location, symbolicName, version, connectContent);
                this.contentURL = url;
            }

            @Override
            protected final Object getKey()
            {
                return contentURL;
            }
        }

        /**
         * Atomos content discovered in an Atomos index.  The key is this content itself
         * which is used to lookup the content based on package name.
         */
        public class AtomosContentIndexed extends AtomosContentBase
        {

            public AtomosContentIndexed(String location, String symbolicName, Version version, ConnectContent content)
            {
                super(location, symbolicName, version, content);
            }

            @Override
            protected final Object getKey()
            {
                // TODO a bit hokey to use ourselves as a key
                return this;
            }
        }
    }

    @Override
    public final Optional<Bundle> getBundle(Class<?> classFromBundle)
    {
        String location = getConnectLocation(classFromBundle);
        if (location != null)
        {
            BundleContext bc = context.get();
            if (bc != null)
            {
                return Optional.ofNullable(bc.getBundle(location));
            }
        }
        return Optional.empty();
    }

    protected final String getConnectLocation(Class<?> classFromBundle)
    {
        lockRead();
        try
        {
            return atomosKeyToConnectLocation.get(getAtomosKey(classFromBundle));
        }
        finally
        {
            unlockRead();
        }
    }

    protected Object getAtomosKey(Class<?> classFromBundle)
    {
        Package pkg = classFromBundle.getPackage();
        if (pkg != null)
        {
            AtomosContentIndexed indexed = packageToAtomosContent.get(pkg.getName());
            if (indexed != null)
            {
                return indexed;
            }
        }
        return classFromBundle.getProtectionDomain().getCodeSource().getLocation();
    }

    protected abstract void filterBasedOnReadEdges(
        AtomosContent atomosContent,
        Collection<BundleCapability> candidates);

    protected final void filterNotVisible(
        AtomosContent atomosContent,
        Collection<BundleCapability> candidates)
    {
        if (atomosContent != null)
        {
            candidates.removeIf(candidate -> !isVisible(atomosContent, candidate));
        }
    }

    private boolean isVisible(
        AtomosContent atomosContent,
        BundleCapability candidate)
    {
        AtomosContent candidateAtomos = getByConnectLocation(
            candidate.getRevision().getBundle().getLocation(), true);
        if (candidateAtomos == null)
        {
            // atomos connected content cannot see normal bundles
            return false;
        }
        else
        {
            AtomosLayer thisLayer = atomosContent.getAtomosLayer();
            return isInLayerHierarchy(thisLayer, candidateAtomos.getAtomosLayer());
        }
    }

    final boolean isInLayerHierarchy(AtomosLayer thisLayer, AtomosLayer candLayer)
    {
        if (thisLayer.equals(candLayer))
        {
            return true;
        }
        for (AtomosLayer parent : thisLayer.getParents())
        {
            if (isInLayerHierarchy(parent, candLayer))
            {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> asSet(Set<? extends T> l)
    {
        return (Set<T>) l;
    }

    Thread saveOnVMExit = new Thread(() -> {
        try
        {
            new AtomosStorage(this).saveLayers(storeRoot.get());
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to save atomos runtime.", e);
        }
    });

    protected void start(BundleContext bc) throws BundleException
    {
        debug("Activating Atomos runtime");
        this.context.set(bc);
        Runtime.getRuntime().addShutdownHook(saveOnVMExit);
        AtomosFrameworkUtilHelper.addHelper(this);

        bc.addBundleListener(this);
        bc.addFrameworkListener(this);
        for (Bundle b : bc.getBundles())
        {
            addPackages(b);
        }

        AtomosFrameworkHooks hooks = new AtomosFrameworkHooks(this);
        bc.registerService(ResolverHookFactory.class, hooks, null);
        bc.registerService(CollisionHook.class, hooks, null);

        boolean installBundles = Boolean.parseBoolean(
            getProperty(bc, AtomosRuntime.ATOMOS_CONTENT_INSTALL, "true"));
        boolean startBundles = Boolean.parseBoolean(
            getProperty(bc, AtomosRuntime.ATOMOS_CONTENT_START, "true"));
        installAtomosContents(getBootLayer(), installBundles, startBundles);
        atomosRuntimeReg = bc.registerService(AtomosRuntime.class, this, null);
        atomosCommandsReg = new AtomosCommands(this).register(bc);
    }

    void addPackages(Bundle b)
    {
        AtomosContentBase atomosContent = (AtomosContentBase) getConnectedContent(
            b.getLocation());
        if (atomosContent instanceof AtomosContentIndexed)
        {
            BundleRevision r = b.adapt(BundleRevision.class);
            r.getDeclaredCapabilities(PackageNamespace.PACKAGE_NAMESPACE).forEach(
                (p) -> packageToAtomosContent.putIfAbsent(
                    (String) p.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE),
                    (AtomosContentIndexed) atomosContent));
            String privatePackages = b.getHeaders("").get("Private-Package");
            if (privatePackages != null)
            {
                for (String pkgName : privatePackages.split(","))
                {
                    pkgName = pkgName.trim();
                    packageToAtomosContent.put(pkgName,
                        (AtomosContentIndexed) atomosContent);
                }
            }
            else
            {
                // ensure content is open
                b.getEntry("does.not.exist");
                ConnectContent content = atomosContent.getConnectContent();
                try
                {
                    content.getEntries().forEach((s) -> {
                        if (s.length() > 1 && s.endsWith("/") && s.indexOf('-') < 0)
                        {
                            String pkg = s.substring(0, s.length() - 1).replace('/', '.');
                            packageToAtomosContent.put(pkg,
                                (AtomosContentIndexed) atomosContent);
                        }
                    });
                }
                catch (IOException e)
                {
                    // ignore
                    debug("IOException getting entries: %s", e.getMessage());
                }
            }
        }
    }
    protected void stop(BundleContext bc) throws BundleException
    {
        debug("Stopping Atomos runtime");
        this.context.compareAndSet(bc, null);
        try
        {
            Runtime.getRuntime().removeShutdownHook(saveOnVMExit);
            new AtomosStorage(this).saveLayers(storeRoot.get());
        }
        catch (IllegalStateException e)
        {
            // ignore this; happens if the JVM already is in the process of running shutdown hooks
            // in that case we can skip saveLayers call
        }
        catch (IOException e)
        {
            throw new BundleException("Failed to save atomos runtime.", e);
        }

        bc.removeBundleListener(this);
        bc.removeFrameworkListener(this);

        AtomosFrameworkUtilHelper.removeHelper(this);
        atomosCommandsReg.unregister();
        atomosRuntimeReg.unregister();
    }

    private String getProperty(BundleContext bc, String key, String defaultValue)
    {
        String result = bc.getProperty(key);
        return result == null ? defaultValue : result;
    }

    private void installAtomosContents(AtomosLayer atomosLayer,
        boolean installBundles,
        boolean startBundles)
    {
        if (installBundles)
        {
            debug("Installing Atomos content.");
            List<Bundle> bundles = new ArrayList<>();
            for (AtomosContent atomosContent : atomosLayer.getAtomosContents())
            {
                if (getBundle(atomosContent) == null)
                {
                    debug("Installing AtomosContent: %s", atomosContent);
                    try
                    {
                        Bundle b = atomosContent.install("atomos");
                        if (b != null && b.getBundleId() != 0)
                        {
                            bundles.add(b);
                        }
                    }
                    catch (BundleException e)
                    {
                        debug("Failed to install to install %s: %s", atomosContent,
                            e.getMessage());
                    }
                }
            }
            if (startBundles)
            {
                for (Bundle b : bundles)
                {
                    debug("Starting connected bundle: %s", b);
                    BundleRevision rev = b.adapt(BundleRevision.class);
                    if ((rev.getTypes() & BundleRevision.TYPE_FRAGMENT) == 0)
                    {
                        try
                        {
                            b.start();
                        }
                        catch (BundleException e)
                        {
                            debug("Failed to install to install %s: %s", e,
                                b,
                                e.getMessage());
                        }
                    }
                }
                for (AtomosLayer child : atomosLayer.getChildren())
                {
                    installAtomosContents(child, installBundles, startBundles);
                }
            }
        }
    }

    public void initialize(File storage, Map<String, String> configuration)
    {
        saveConfig(configuration);
        if (!storeRoot.compareAndSet(null, storage))
        {
            throw new IllegalStateException(
                "This AtomosRuntime is already being used by store: " + storeRoot.get());
        }
        try
        {
            new AtomosStorage(this).loadLayers(storage);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private void saveConfig(Map<String, String> configuration) {
        configuration.forEach((k,v) -> {
            if (k != null && k.startsWith(ATOMOS_PROP_PREFIX) && v != null)
            {
                config.put(k, v);
            }
        });
    }

    public void debug(String message, BundleException e, Object... args)
    {
        debug(message, args);
        if (DEBUG)
        {
            e.printStackTrace();
        }
    }
    public void debug(String message, Object... args)
    {
        if (DEBUG)
        {
            try
            {
                System.out.println("ATOMOS DEBUG: " + String.format(message, args));
            }
            catch (Throwable t)
            {
                t.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E
    {
        throw (E) e;
    }
}
