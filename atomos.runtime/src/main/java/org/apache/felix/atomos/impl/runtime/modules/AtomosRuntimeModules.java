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
package org.apache.felix.atomos.impl.runtime.modules;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase;
import org.apache.felix.atomos.runtime.AtomosContent;
import org.apache.felix.atomos.runtime.AtomosLayer;
import org.apache.felix.atomos.runtime.AtomosLayer.LoaderType;
import org.apache.felix.atomos.runtime.AtomosRuntime;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.BundleCapability;

public class AtomosRuntimeModules extends AtomosRuntimeBase
{
    private final Module thisModule = AtomosRuntimeModules.class.getModule();
    private final Configuration thisConfig = thisModule.getLayer() == null ? null
        : thisModule.getLayer().configuration();
    private final Map<Configuration, AtomosLayerBase> byConfig = new HashMap<>();
    private final AtomosLayer bootLayer = createBootLayer();

    public AtomosRuntimeModules(Map<String, String> config)
    {
        super(config);
    }

    private AtomosLayer createBootLayer()
    {
        return createAtomosLayer(thisConfig, "boot", -1, LoaderType.SINGLE);
    }

    AtomosLayerBase getByConfig(Configuration config)
    {
        lockRead();
        try
        {
            return byConfig.get(config);
        }
        finally
        {
            unlockRead();
        }
    }

    @Override
    protected AtomosLayer addLayer(List<AtomosLayer> parents, String name, long id,
        LoaderType loaderType, Path... paths)
    {
        if (parents.isEmpty())
        {
            throw new IllegalArgumentException("Must specify at least one parent layer.");
        }
        if (bootLayer.adapt(ModuleLayer.class).isEmpty())
        {
            throw new UnsupportedOperationException(
                "Cannot add module layers when Atomos is not loaded as module.");
        }
        List<Configuration> parentConfigs = parents.stream().map(
            (l) -> l.adapt(ModuleLayer.class).get().configuration()).collect(
                Collectors.toList());
        ModuleFinder finder = ModuleFinder.of(paths);
        List<String> roots = finder.findAll().stream().map(
            (m) -> m.descriptor().name()).collect(Collectors.toList());
        Configuration config = Configuration.resolve(ModuleFinder.of(), parentConfigs,
            ModuleFinder.of(paths), roots);
        return createAtomosLayer(config, name, id, loaderType, paths);
    }

    @Override
    public ConnectFrameworkFactory findFrameworkFactory()
    {
        ServiceLoader<ConnectFrameworkFactory> loader;
        if (AtomosRuntime.class.getModule().getLayer() == null)
        {
            loader = ServiceLoader.load(ConnectFrameworkFactory.class,
                getClass().getClassLoader());
        }
        else
        {
            loader = ServiceLoader.load( //
                getClass().getModule().getLayer(), //
                ConnectFrameworkFactory.class);
        }
        return loader.findFirst() //
            .orElseThrow(
                () -> new RuntimeException("No Framework implementation found."));
    }

    AtomosLayerBase createAtomosLayer(Configuration config, String name, long id,
        LoaderType loaderType, Path... paths)
    {
        AtomosLayerBase existing = getByConfig(config);
        if (existing != null)
        {
            return existing;
        }
        existing = getById(id);
        if (existing != null)
        {
            throw new IllegalArgumentException("The a layer already exists with the id: "
                + id + " " + existing.getName());
        }
        lockWrite();
        try
        {
            List<AtomosLayer> parents = findParents(config, name);
            id = id < 0 ? nextLayerId.getAndIncrement() : id;
            if (Configuration.empty().equals(config))
            {
                name = "empty";
            }
            AtomosLayerModules result = new AtomosLayerModules(config, parents, id, name,
                loaderType, paths);
            addAtomosLayer(result);
            return result;
        }
        finally
        {
            unlockWrite();
        }
    }

    private List<AtomosLayer> findParents(Configuration config, String name)
    {
        if (config == null || config.parents().isEmpty())
        {
            return List.of();
        }
        List<AtomosLayer> found = new ArrayList<>(config.parents().size());
        for (Configuration parentConfig : config.parents())
        {
            AtomosLayerBase existingParent = getByConfig(parentConfig);
            if (existingParent != null)
            {
                found.add(existingParent);
            }
            else
            {
                // If it didn't exist already we really don't know what type of loader it is;
                // just use SINGLE for now
                // We also don't know what paths it could be using
                found.add(createAtomosLayer(parentConfig, name, -1, LoaderType.SINGLE));
            }
        }
        return Collections.unmodifiableList(found);
    }

    @Override
    protected void addingLayer(AtomosLayerBase atomosLayer)
    {
        Configuration config = atomosLayer.adapt(ModuleLayer.class).map(
            ModuleLayer::configuration).orElse(null);
        if (byConfig.putIfAbsent(config, atomosLayer) != null)
        {
            throw new IllegalStateException(
                "AtomosLayer already exists for configuration.");
        }
    }

    @Override
    protected void removedLayer(AtomosLayerBase atomosLayer)
    {
        byConfig.remove(
            atomosLayer.adapt(ModuleLayer.class).map(ModuleLayer::configuration).orElse(
                null));
    }

    ModuleLayer findModuleLayer(Configuration config, List<AtomosLayer> parents,
        LoaderType loaderType)
    {
        if (config == null)
        {
            return null;
        }
        if (config.equals(thisConfig))
        {
            return thisModule.getLayer();
        }
        if (Configuration.empty().equals(config))
        {
            return ModuleLayer.empty();
        }
        List<ModuleLayer> parentLayers = parents.stream().sequential().map(
            (a) -> a.adapt(ModuleLayer.class).get()).collect(Collectors.toList());
        ModuleLayer.Controller controller;
        switch (loaderType)
        {
            case SINGLE:
                return ModuleLayer.defineModulesWithOneLoader(config, parentLayers,
                    null).layer();
            case OSGI:
                ConcurrentHashMap<String, ModuleConnectLoader> classLoaders = new ConcurrentHashMap<>();
                Function<String, ClassLoader> clf = (moduleName) -> {
                    ResolvedModule m = config.findModule(moduleName).orElse(null);
                    if (m == null || m.configuration() != config)
                    {
                        return null;
                    }

                    return classLoaders.computeIfAbsent(moduleName, (mn) -> {
                        try
                        {
                            return new ModuleConnectLoader(m, this);
                        }
                        catch (IOException e)
                        {
                            throw new UncheckedIOException(e);
                        }
                    });
                };
                controller = ModuleLayer.defineModules(config, parentLayers, clf);
                controller.layer().modules().forEach((m) -> {
                    ModuleConnectLoader loader = (ModuleConnectLoader) m.getClassLoader();
                    loader.initEdges(m, config, classLoaders);
                });
                return controller.layer();
            case MANY:
                return ModuleLayer.defineModulesWithManyLoaders(config, parentLayers,
                    null).layer();
            default:
                throw new UnsupportedOperationException(loaderType.toString());
        }
    }

    @Override
    public AtomosLayer getBootLayer()
    {
        return bootLayer;
    }

    @Override
    protected Object getAtomosKey(Class<?> classFromBundle)
    {
        Module m = classFromBundle.getModule();
        if (m != null && m.isNamed())
        {
            return m;
        }
        return super.getAtomosKey(classFromBundle);
    }

    private static Entry<String, Version> getBSNVersion(ResolvedModule m)
    {
        try (ModuleReader reader = m.reference().open())
        {
            return reader.find("META-INF/MANIFEST.MF").map(
                (mf) -> getManifestBSNVersion(mf, m)).orElseGet(
                    () -> new SimpleEntry<>(getBSN(m, null), getVersion(m, null)));
        }
        catch (IOException e)
        {
            return new SimpleEntry<>(getBSN(m, null), getVersion(m, null));
        }
    }

    private static Entry<String, Version> getManifestBSNVersion(URI manifest,
        ResolvedModule resolved)
    {
        try (InputStream is = manifest.toURL().openStream())
        {
            Attributes headers = new Manifest(is).getMainAttributes();
            return new SimpleEntry<>(getBSN(resolved, headers),
                getVersion(resolved, headers));
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private static String getBSN(ResolvedModule resolved, Attributes headers)
    {
        String bsnHeader = headers != null
            ? headers.getValue(Constants.BUNDLE_SYMBOLICNAME)
            : null;
        if (bsnHeader == null)
        {
            return resolved.name();
        }
        int bsnEnd = bsnHeader.indexOf(';');
        return bsnEnd < 0 ? bsnHeader.trim() : bsnHeader.substring(0, bsnEnd).trim();
    }

    private static Version getVersion(ResolvedModule resolved, Attributes headers)
    {
        String sVersion = headers != null ? headers.getValue(Constants.BUNDLE_VENDOR)
            : null;
        if (sVersion == null)
        {
            sVersion = resolved.reference().descriptor().version().map(
                java.lang.module.ModuleDescriptor.Version::toString).orElse("0");
        }
        try
        {
            return Version.valueOf(sVersion);
        }
        catch (IllegalArgumentException e)
        {
            return Version.emptyVersion;
        }
    }

    @Override
    protected void filterBasedOnReadEdges(AtomosContent atomosContent,
        Collection<BundleCapability> candidates)
    {
        if (atomosContent == null)
        {
            // only do this for atomos contents
            return;
        }
        Module m = atomosContent.adapt(Module.class).orElse(null);
        if (m == null)
        {
            filterNotVisible(atomosContent, candidates);
        }
        else
        {
            for (Iterator<BundleCapability> iCands = candidates.iterator(); iCands.hasNext();)
            {
                BundleCapability candidate = iCands.next();
                AtomosContent candidateAtomos = getByConnectLocation(
                    candidate.getRevision().getBundle().getLocation(), true);
                if (candidateAtomos == null
                    || candidateAtomos.adapt(Module.class).isEmpty())
                {
                    iCands.remove();
                }
                else
                {
                    if (!m.canRead(candidateAtomos.adapt(Module.class).get()))
                    {
                        iCands.remove();
                    }
                }
            }
        }
    }

    public class AtomosLayerModules extends AtomosLayerBase
    {
        private final ModuleLayer moduleLayer;
        private final Set<AtomosContentBase> atomosBundles;
        private final Map<Module, AtomosContentBase> atomosModules;

        AtomosLayerModules(Configuration config, List<AtomosLayer> parents, long id, String name, LoaderType loaderType, Path... paths)
        {
            super(parents, id, name, loaderType, paths);
            moduleLayer = findModuleLayer(config, parents, loaderType);
            atomosBundles = findAtomosLayerContent();
            atomosModules = atomosBundles.stream().filter(
                a -> a.adapt(Module.class).isPresent()).collect(
                    Collectors.toUnmodifiableMap((k) -> k.adapt(Module.class).get(),
                        (v) -> v));
        }

        @Override
        public boolean isAddLayerSupported()
        {
            return thisConfig != null;
        }

        @Override
        public AtomosLayer addModules(String name, Path path)
        {
            if (isAddLayerSupported())
            {
                if (path == null)
                {
                    ResolvedModule resolved = thisConfig.findModule(
                        AtomosRuntime.class.getModule().getName()).get();
                    URI location = resolved.reference().location().get();
                    if (location.getScheme().equals("file"))
                    {
                        // Use the module location as the relative base to locate the modules folder
                        File thisModuleFile = new File(location);
                        File candidate = new File(thisModuleFile.getParent(), name);
                        path = candidate.isDirectory() ? candidate.toPath() : null;
                    }
                }
                if (path != null)
                {
                    return addLayer(name, LoaderType.OSGI, path);
                }
                else
                {
                    return null;
                }
            }
            else
            {
                return super.addModules(name, path);
            }
        }

        private Set<AtomosContentBase> findAtomosLayerContent()
        {
            return moduleLayer == null ? findAtomosContents()
                : findModuleLayerAtomosBundles(moduleLayer);
        }

        private Set<AtomosContentBase> findModuleLayerAtomosBundles(
            ModuleLayer searchLayer)
        {
            Set<AtomosContentBase> found = new LinkedHashSet<>();
            Map<ModuleDescriptor, Module> descriptorMap = searchLayer.modules().stream().collect(
                Collectors.toMap(Module::getDescriptor, m -> (m)));
            for (ResolvedModule resolved : searchLayer.configuration().modules())
            {
                // include only if it is not excluded
                Module m = descriptorMap.get(resolved.reference().descriptor());
                if (m == null)
                {
                    continue;
                }

                String location;
                if (m.getDescriptor().provides().stream().anyMatch(
                    (p) -> FrameworkFactory.class.getName().equals(p.service())))
                {
                    // we assume a module that provides the FrameworkFactory is the system bundle
                    location = Constants.SYSTEM_BUNDLE_LOCATION;
                }
                else
                {
                    location = resolved.reference().location().map((u) -> {
                        StringBuilder sb = new StringBuilder();
                        if (!getName().isEmpty())
                        {
                            sb.append(getName()).append(':');
                        }
                        sb.append(u.toString());
                        return sb.toString();
                    }).orElse(null);
                }
                if (location == null)
                {
                    continue;
                }

                Entry<String, Version> bsnVersion = getBSNVersion(resolved);
                found.add(new AtomosContentModule(resolved, m, location,
                    bsnVersion.getKey(), bsnVersion.getValue()));

            }

            return Collections.unmodifiableSet(found);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<T> adapt(Class<T> type)
        {
            if (ModuleLayer.class.equals(type))
            {
                return Optional.ofNullable((T) moduleLayer);
            }
            return super.adapt(type);
        }

        /**
         * Atomos content discovered on the module path.  The key is the module
         * of this Atomos content.
         *
         */
        public class AtomosContentModule extends AtomosContentBase
        {

            /**
             * The module for this atomos content.
             */
            private final Module module;

            public AtomosContentModule(ResolvedModule resolvedModule, Module module, String location, String symbolicName, Version version)
            {
                super(location, symbolicName, version, new ConnectContentModule(module,
                    resolvedModule.reference(), AtomosLayerModules.this, symbolicName,
                    version));
                this.module = module;
            }

            @Override
            protected final Object getKey()
            {
                return module;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> Optional<T> adapt(Class<T> type)
            {
                if (Module.class.equals(type))
                {
                    return Optional.ofNullable((T) module);
                }
                return super.adapt(type);
            }
        }

        @Override
        public Set<AtomosContent> getAtomosContents()
        {
            return asSet(atomosBundles);
        }

        @Override
        protected void findBootModuleLayerAtomosContents(Set<AtomosContentBase> result)
        {
            result.addAll(findModuleLayerAtomosBundles(ModuleLayer.boot()));
        }

        AtomosContentBase getAtomosContent(Module m)
        {
            AtomosContentBase result = atomosModules.get(m);
            if (result != null)
            {
                return result;
            }
            for (AtomosLayer parent : getParents())
            {
                if (parent instanceof AtomosLayerModules)
                {
                    result = ((AtomosLayerModules) parent).getAtomosContent(m);
                }
                if (result != null)
                {
                    return result;
                }
            }
            return null;
        }
    }

    public Bundle getBundle(Module module)
    {
        if (module == null)
        {
            return null;
        }
        String location;
        lockRead();
        try
        {
            location = atomosKeyToConnectLocation.get(module);
            if (location == null)
            {
                return null;
            }
        }
        finally
        {
            unlockRead();
        }
        BundleContext bc = getBundleContext();
        if (bc == null)
        {
            return null;
        }
        return bc.getBundle(location);
    }

    @Override
    public void populateConfig(Map<String, String> config)
    {
        super.populateConfig(config);
        if (config.get(Constants.FRAMEWORK_SYSTEMPACKAGES) == null)
        {
            // this is to prevent the framework from exporting all the packages provided
            // by the module path.
            config.put(Constants.FRAMEWORK_SYSTEMPACKAGES, "");
        }
    }
}
