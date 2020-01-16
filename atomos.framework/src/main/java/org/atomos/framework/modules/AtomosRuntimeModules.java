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
package org.atomos.framework.modules;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime;
import org.atomos.framework.base.AtomosRuntimeBase;
import org.atomos.framework.base.JavaServiceNamespace;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.connect.ConnectContent.ConnectEntry;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.resource.Namespace;

public class AtomosRuntimeModules extends AtomosRuntimeBase
{
    private static final String OSGI_CONTRACT_NAMESPACE = "osgi.contract";
    private static final String OSGI_VERSION_ATTR = "version:Version";
    private final Module thisModule = AtomosRuntimeModules.class.getModule();
    private final Configuration thisConfig = thisModule.getLayer() == null ? null
        : thisModule.getLayer().configuration();
    private final Map<Configuration, AtomosLayerBase> byConfig = new HashMap<>();
    private final AtomosLayer bootLayer = createBootLayer();

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
    protected ConnectFrameworkFactory findFrameworkFactory()
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
        ConnectFrameworkFactory factory = loader.findFirst() //
            .orElseThrow(
                () -> new RuntimeException("No Framework implementation found."));
        return factory;
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
            (m) -> m.configuration()).orElse(null);
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
            atomosLayer.adapt(ModuleLayer.class).map((l) -> l.configuration()).orElse(
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
    public AtomosLayer addModules(String name, Path path)
    {
        if (modulesSupported())
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
                    File candidate = new File(thisModuleFile.getParent(), "modules");
                    path = candidate.isDirectory() ? candidate.toPath() : null;
                }
            }
            if (path != null)
            {
                return addLayer(Collections.singletonList(getBootLayer()), "modules",
                    LoaderType.OSGI, path);
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

    @Override
    public boolean modulesSupported()
    {
        return thisConfig != null;
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

    protected Optional<Map<String, String>> createManifest(ConnectContent connectContent,
        Module module)
    {
        return Optional.of(connectContent.getEntry("META-INF/MANIFEST.MF").map(
            (mf) -> createManifest(mf, module)).orElseGet(
                () -> createManifest((ConnectEntry) null, module)));

    }

    private Map<String, String> createManifest(ConnectEntry mfEntry, Module module)
    {
        Map<String, String> result = new HashMap<>();
        if (mfEntry != null)
        {
            try
            {
                Manifest mf = new Manifest(mfEntry.getInputStream());
                Attributes mainAttrs = mf.getMainAttributes();
                for (Object key : mainAttrs.keySet())
                {
                    Name name = (Name) key;
                    result.put(name.toString(), mainAttrs.getValue(name));
                }
            }
            catch (IOException e)
            {
                throw new UncheckedIOException("Error reading connect manfest.", e);
            }
        }

        ModuleDescriptor desc = module.getDescriptor();
        StringBuilder capabilities = new StringBuilder();
        StringBuilder requirements = new StringBuilder();
        String bsn = result.get(Constants.BUNDLE_SYMBOLICNAME);
        if (bsn == null)
        {
            // set the symbolic name for the module; don't allow fragments to attach
            result.put(Constants.BUNDLE_SYMBOLICNAME,
                desc.name() + "; " + Constants.FRAGMENT_ATTACHMENT_DIRECTIVE + ":="
                    + Constants.FRAGMENT_ATTACHMENT_NEVER);

            // set the version; use empty version in case of missing or format issues
            Version version = desc.version().map((v) -> {
                try
                {
                    return Version.valueOf(v.toString());
                }
                catch (IllegalArgumentException e)
                {
                    return Version.emptyVersion;
                }
            }).orElse(Version.emptyVersion);
            result.put(Constants.BUNDLE_VERSION, version.toString());

            // only do exports for non bundle modules
            // real OSGi bundles already have good export capabilities
            StringBuilder exportPackageHeader = new StringBuilder();
            for (Exports exports : desc.exports())
            {
                if (exportPackageHeader.length() > 0)
                {
                    exportPackageHeader.append(", ");
                }
                exportPackageHeader.append(exports.source());
                // TODO map targets to x-friends directive?
            }
            if (exportPackageHeader.length() > 0)
            {
                result.put(Constants.EXPORT_PACKAGE, exportPackageHeader.toString());
            }

            // add a contract based on the module name
            addOSGiContractCapability(capabilities, desc, version.toString());

            // only do requires for non bundle modules
            // map requires to require bundle
            StringBuilder requireBundleHeader = new StringBuilder();
            for (Requires requires : desc.requires())
            {
                if (requireBundleHeader.length() > 0)
                {
                    requireBundleHeader.append(", ");
                }

                requireBundleHeader.append(requires.name()).append("; ");
                // determine the resolution value based on the STATIC modifier
                String resolution = requires.modifiers().contains(
                    Requires.Modifier.STATIC) ? Namespace.RESOLUTION_OPTIONAL
                        : Namespace.RESOLUTION_MANDATORY;
                requireBundleHeader.append(Constants.RESOLUTION_DIRECTIVE).append(
                    ":=").append(resolution).append("; ");
                // determine the visibility value based on the TRANSITIVE modifier
                String visibility = requires.modifiers().contains(
                    Requires.Modifier.TRANSITIVE) ? BundleNamespace.VISIBILITY_REEXPORT
                        : BundleNamespace.VISIBILITY_PRIVATE;
                requireBundleHeader.append(Constants.VISIBILITY_DIRECTIVE).append(
                    ":=").append(visibility);

            }
            if (requireBundleHeader.length() > 0)
            {
                result.put(Constants.REQUIRE_BUNDLE, requireBundleHeader.toString());
            }
        }
        else
        {
            String origCaps = result.get(Constants.PROVIDE_CAPABILITY);
            if (origCaps != null)
            {
                capabilities.append(origCaps);
            }
            String origReqs = result.get(Constants.REQUIRE_CAPABILITY);
            if (origReqs != null)
            {
                requirements.append(origReqs);
            }
        }
        // map provides to a made up namespace only to give proper resolution errors
        // (although JPMS will likely complain first
        for (Provides provides : desc.provides())
        {
            if (capabilities.length() > 0)
            {
                capabilities.append(", ");
            }
            capabilities.append(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE).append("; ");
            capabilities.append(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE).append(
                "=").append(provides.service()).append("; ");
            capabilities.append(JavaServiceNamespace.CAPABILITY_PROVIDES_WITH).append(
                "=\"").append(
                    provides.providers().stream().collect(
                        Collectors.joining(","))).append("\"");
        }

        // map uses to a made up namespace only to give proper resolution errors
        // (although JPMS will likely complain first)
        for (String uses : desc.uses())
        {
            if (requirements.length() > 0)
            {
                requirements.append(", ");
            }
            requirements.append(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE).append("; ");
            requirements.append(Constants.RESOLUTION_DIRECTIVE).append(":=").append(
                Constants.RESOLUTION_OPTIONAL).append("; ");
            requirements.append(Constants.FILTER_DIRECTIVE).append(":=").append(
                "\"(").append(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE).append(
                    "=").append(uses).append(")\"");
        }

        if (capabilities.length() > 0)
        {
            result.put(Constants.PROVIDE_CAPABILITY, capabilities.toString());
        }
        if (requirements.length() > 0)
        {
            result.put(Constants.REQUIRE_CAPABILITY, requirements.toString());
        }
        return result;
    }

    private void addOSGiContractCapability(StringBuilder capabilities,
        ModuleDescriptor desc, String version)
    {
        if (capabilities.length() > 0)
        {
            capabilities.append(", ");
        }
        capabilities.append(OSGI_CONTRACT_NAMESPACE).append("; ").append(
            OSGI_VERSION_ATTR).append("=").append(version);
        Set<Exports> exports = desc.exports();
        if (!exports.isEmpty())
        {
            String uses = exports.stream().map(e -> e.source()).sorted().collect(
                Collectors.joining(","));
            capabilities.append("; ").append(Namespace.CAPABILITY_USES_DIRECTIVE).append(
                "=\"").append(uses).append("\"");
        }
    }

    @Override
    protected void filterBasedOnReadEdges(AtomosBundleInfo atomosBundle,
        Collection<BundleCapability> candidates)
    {
        if (atomosBundle == null)
        {
            // only do this for atomos bundles
            return;
        }
        Module m = atomosBundle.adapt(Module.class).orElse(null);
        if (m == null)
        {
            filterNotVisible(atomosBundle, candidates);
        }
        else
        {
            for (Iterator<BundleCapability> iCands = candidates.iterator(); iCands.hasNext();)
            {
                BundleCapability candidate = iCands.next();
                AtomosBundleInfo candidateAtomos = getByOSGiLocation(
                    candidate.getRevision().getBundle().getLocation());
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
        private final Set<AtomosBundleInfoBase> atomosBundles;

        AtomosLayerModules(Configuration config, List<AtomosLayer> parents, long id, String name, LoaderType loaderType, Path... paths)
        {
            super(parents, id, name, loaderType, paths);
            moduleLayer = findModuleLayer(config, parents, loaderType);
            atomosBundles = findAtomosBundles();
        }

        private Set<AtomosBundleInfoBase> findAtomosBundles()
        {
            return moduleLayer == null ? findClassPathAtomosBundles()
                : findModuleLayerAtomosBundles(moduleLayer);
        }

        private Set<AtomosBundleInfoBase> findModuleLayerAtomosBundles(
            ModuleLayer searchLayer)
        {
            Map<ModuleDescriptor, Module> descriptorMap = searchLayer.modules().stream().collect(
                Collectors.toMap(Module::getDescriptor, m -> (m)));
            Set<AtomosBundleInfoBase> found = new LinkedHashSet<>();
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

                Version version = resolved.reference().descriptor().version().map((v) -> {
                    try
                    {
                        return Version.valueOf(v.toString());
                    }
                    catch (IllegalArgumentException e)
                    {
                        return Version.emptyVersion;
                    }
                }).orElse(Version.emptyVersion);

                found.add(new AtomosBundleInfoModule(resolved, m, location,
                    resolved.name(), version));

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

        public class AtomosBundleInfoModule extends AtomosBundleInfoBase
        {

            /**
             * The module for this atomos bundle.
             */
            private final Module module;

            public AtomosBundleInfoModule(ResolvedModule resolvedModule, Module module, String location, String symbolicName, Version version)
            {
                super(location, symbolicName, version, new ModuleConnectContent(module,
                    resolvedModule.reference(), AtomosRuntimeModules.this));
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
        public Set<AtomosBundleInfo> getAtomosBundles()
        {
            return asSet(atomosBundles);
        }

        @Override
        protected void findBootLayerAtomosBundles(Set<AtomosBundleInfoBase> result)
        {
            result.addAll(findModuleLayerAtomosBundles(ModuleLayer.boot()));
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
            location = atomosKeyToOSGiLocation.get(module);
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
}
