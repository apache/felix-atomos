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
package org.apache.felix.atomos.impl.modules;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
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
import java.util.stream.Collectors;

import org.apache.felix.atomos.Atomos;
import org.apache.felix.atomos.AtomosContent;
import org.apache.felix.atomos.AtomosLayer;
import org.apache.felix.atomos.AtomosLayer.LoaderType;
import org.apache.felix.atomos.impl.base.AtomosBase;
import org.apache.felix.atomos.impl.base.JavaServiceNamespace;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.resource.Namespace;

public class AtomosModules extends AtomosBase
{
    private final static String ATOMOS_GENERATED = "Atomos-GeneratedManifest";
    private final static String ATOMOS_TEMPORARY_GENERATED_REQUIRES = "Atomos-TemporaryGeneratedRequires";
    private final Module thisModule = AtomosModules.class.getModule();
    private final Configuration thisConfig = thisModule.getLayer() == null ? null
        : thisModule.getLayer().configuration();
    private final Map<Configuration, AtomosLayerBase> byConfig = new HashMap<>();
    private final AtomosLayer bootLayer = createBootLayer();

    public AtomosModules(Map<String, String> config, HeaderProvider headerProvider)
    {
        super(config, headerProvider);
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
        if (Atomos.class.getModule().getLayer() == null)
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
            for (AtomosContentBase content : atomosBundles)
            {
                final Module m = content.adapt(Module.class).orElse(null);
                if (m == null)
                {
                    continue;
                }
                content.getConnectContent().getHeaders().ifPresent(headers -> {
                    if (Boolean.parseBoolean(headers.get(ATOMOS_GENERATED)) &&
                        Boolean.parseBoolean(headers.get(ATOMOS_TEMPORARY_GENERATED_REQUIRES)))
                    {
                        calculateRequires(headers, m, (requires) -> {
                            return m.getLayer().findModule(requires).map(
                                requiredModule -> {
                                    return getAtomosContent(
                                        requiredModule).getSymbolicName();
                                }).orElse(requires);
                        });
                        headers.remove(ATOMOS_TEMPORARY_GENERATED_REQUIRES);
                    }
                });
            }
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
                        Atomos.class.getModule().getName()).get();
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

                ManifestHolder holder = new ManifestHolder();

                ConnectContent content = new ConnectContentModule(m, resolved.reference(), AtomosLayerModules.this, holder::getHeaders);

                Map<String, String> headers;
                try
                {
                    content.open();
                    try
                    {
                        headers = getRawHeaders(content);
                    }
                    finally
                    {
                        content.close();
                    }
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException("Error reading connect manifest.", e);
                }



                generateHeaders(headers, m);

                headers = applyHeaderProvider(holder, location, headers);

                String symbolicName = headers.get(Constants.BUNDLE_SYMBOLICNAME);
                if (symbolicName != null)
                {
                    int semiColon = symbolicName.indexOf(';');
                    if (semiColon != -1)
                    {
                        symbolicName = symbolicName.substring(0, semiColon);
                    }
                    symbolicName = symbolicName.trim();

                    Version version = Version.parseVersion(
                        headers.get(Constants.BUNDLE_VERSION));

                    found.add(new AtomosContentModule(m, location,
                            symbolicName, version, content));
                }
            }

            return Collections.unmodifiableSet(found);
        }

        private void generateHeaders(Map<String, String> headers, Module m)
        {
            ModuleDescriptor desc = m.getDescriptor();
            StringBuilder capabilities = new StringBuilder();
            StringBuilder requirements = new StringBuilder();
            String bsn = headers.get(Constants.BUNDLE_SYMBOLICNAME);
            if (bsn == null)
            {
                // NOTE that we depend on the framework connect implementation to allow connect bundles
                // to export java.* packages
                headers.put(Constants.BUNDLE_MANIFESTVERSION, "2");
                // set the symbolic name for the module; don't allow fragments to attach
                headers.put(Constants.BUNDLE_SYMBOLICNAME,
                    desc.name() + "; " + Constants.FRAGMENT_ATTACHMENT_DIRECTIVE + ":="
                        + Constants.FRAGMENT_ATTACHMENT_NEVER);

                // set the version
                Version v;
                try
                {
                    v = Version.parseVersion(desc.version().map(
                        java.lang.module.ModuleDescriptor.Version::toString).orElse("0"));
                }
                catch (IllegalArgumentException e)
                {
                    v = Version.emptyVersion;
                }
                headers.put(Constants.BUNDLE_VERSION, v.toString());

                // only do exports for non bundle modules
                // real OSGi bundles already have good export capabilities
                StringBuilder exportPackageHeader = new StringBuilder();

                // ModuleDescriptor.exports() is empty for an automatic module, which is different from
                // JPMS at runtime where all packages in the module are exported for an automatic module
                if (desc.isAutomatic()) {
                    desc.packages().stream().sorted().forEach((packages) -> {
                        if (exportPackageHeader.length() > 0)
                        {
                            exportPackageHeader.append(", ");
                        }
                        exportPackageHeader.append(packages);
                    });
                }
                else {
                    desc.exports().stream().sorted().forEach((exports) -> {
                        if (exportPackageHeader.length() > 0)
                        {
                            exportPackageHeader.append(", ");
                        }
                        exportPackageHeader.append(exports.source());
                        // TODO map targets to x-friends directive?
                    });
                }
                if (exportPackageHeader.length() > 0)
                {
                    headers.put(Constants.EXPORT_PACKAGE, exportPackageHeader.toString());
                }

                // Note that for generated manifests based of module descriptor
                // will have their requires calculated later.
                // Place a header indicating this is generated
                headers.put(ATOMOS_GENERATED, Boolean.TRUE.toString());
                if (calculateRequires(headers, m, Function.identity()))
                {
                    // Set a temporary header if we need to recalculate later
                    headers.put(ATOMOS_TEMPORARY_GENERATED_REQUIRES, Boolean.TRUE.toString());
                }
            }
            else
            {
                String origCaps = headers.get(Constants.PROVIDE_CAPABILITY);
                if (origCaps != null)
                {
                    capabilities.append(origCaps);
                }
                String origReqs = headers.get(Constants.REQUIRE_CAPABILITY);
                if (origReqs != null)
                {
                    requirements.append(origReqs);
                }
            }
            // map provides to a made up namespace only to give proper resolution errors
            // (although JPMS will likely complain first
            for (ModuleDescriptor.Provides provides : desc.provides())
            {
                if (capabilities.length() > 0)
                {
                    capabilities.append(", ");
                }
                capabilities.append(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE).append(
                    "; ");
                capabilities.append(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE).append(
                    "=").append(provides.service()).append("; ");
                capabilities.append(
                    JavaServiceNamespace.CAPABILITY_PROVIDES_WITH_ATTRIBUTE).append(
                        "=\"").append(String.join(",", provides.providers())).append(
                            "\"");
            }

            // map uses to a made up namespace only to give proper resolution errors
            // (although JPMS will likely complain first)
            for (String uses : desc.uses())
            {
                if (requirements.length() > 0)
                {
                    requirements.append(", ");
                }
                requirements.append(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE).append(
                    "; ");
                requirements.append(Constants.RESOLUTION_DIRECTIVE).append(":=").append(
                    Constants.RESOLUTION_OPTIONAL).append("; ");
                requirements.append(Constants.FILTER_DIRECTIVE).append(":=").append(
                    "\"(").append(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE).append(
                        "=").append(uses).append(")\"");
            }

            if (capabilities.length() > 0)
            {
                headers.put(Constants.PROVIDE_CAPABILITY, capabilities.toString());
            }
            if (requirements.length() > 0)
            {
                headers.put(Constants.REQUIRE_CAPABILITY, requirements.toString());
            }
        }

        private boolean calculateRequires(Map<String, String> headers, Module m,
            Function<String, String> mapper)
        {
            // only do requires for non bundle modules
            // map requires to require bundle
            StringBuilder requireBundleHeader = new StringBuilder();
            for (Requires requires : m.getDescriptor().requires())
            {
                if (requireBundleHeader.length() > 0)
                {
                    requireBundleHeader.append(", ");
                }
                // before requiring based on the name make sure the required
                // module has a BSN that is the same
                String mapping = mapper.apply(requires.name());
                requireBundleHeader.append(mapping).append("; ");
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
                headers.put(Constants.REQUIRE_BUNDLE, requireBundleHeader.toString());
                return true;
            }
            else
            {
                return false;
            }
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

            public AtomosContentModule(Module module, String location, String symbolicName, Version version, ConnectContent content)
            {
                super(location, symbolicName, version, content);
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
