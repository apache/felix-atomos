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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

import org.apache.felix.atomos.impl.runtime.base.JavaServiceNamespace;
import org.apache.felix.atomos.impl.runtime.modules.AtomosRuntimeModules.AtomosLayerModules;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.resource.Namespace;

public class ConnectContentModule implements ConnectContent
{
    final Module module;
    final ModuleReference reference;
    final AtomosLayerModules atomosLayer;
    final String symbolicName;
    final Version version;
    final AtomicReference<Optional<Map<String, String>>> headers = new AtomicReference<>();
    volatile ModuleReader reader = null;

    public ConnectContentModule(Module module, ModuleReference reference, AtomosLayerModules atomosLayer, String symbolicName, Version version)
    {
        this.module = module;
        this.reference = reference;
        this.atomosLayer = atomosLayer;
        this.symbolicName = symbolicName;
        this.version = version;
    }

    @Override
    public void open() throws IOException
    {
        reader = reference.open();
    }

    @Override
    public void close() throws IOException
    {
        ModuleReader current = reader;
        if (current != null)
        {
            reader = null;
            current.close();
        }
    }

    @Override
    public Optional<ClassLoader> getClassLoader()
    {
        return Optional.ofNullable(module.getClassLoader());
    }

    @Override
    public Iterable<String> getEntries() throws IOException
    {
        return () -> {
            try
            {
                return currentReader().list().iterator();
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        };

    }

    private ModuleReader currentReader() throws IOException
    {
        ModuleReader current = reader;
        if (current == null)
        {
            throw new IOException("Reader is not open.");
        }
        return current;
    }

    @Override
    public Optional<ConnectEntry> getEntry(String name)
    {
        try
        {
            return currentReader().find(name).map((u) -> new ModuleConnectEntry(name, u));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

    }

    @Override
    public Optional<Map<String, String>> getHeaders()
    {
        return headers.updateAndGet((h) -> {
            if (h == null)
            {
                h = createManifest();
            }
            return h;
        });
    }

    private Optional<Map<String, String>> createManifest()
    {
        return Optional.of(getEntry("META-INF/MANIFEST.MF").map(
            (mf) -> createManifest(mf)).orElseGet(() -> createManifest(null)));

    }

    private Map<String, String> createManifest(ConnectEntry mfEntry)
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
            // NOTE that we depend on the framework connect implementation to allow connect bundles
            // to export java.* packages
            result.put(Constants.BUNDLE_MANIFESTVERSION, "2");
            // set the symbolic name for the module; don't allow fragments to attach
            result.put(Constants.BUNDLE_SYMBOLICNAME,
                symbolicName + "; " + Constants.FRAGMENT_ATTACHMENT_DIRECTIVE + ":="
                    + Constants.FRAGMENT_ATTACHMENT_NEVER);

            // set the version
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

            // only do requires for non bundle modules
            // map requires to require bundle
            StringBuilder requireBundleHeader = new StringBuilder();
            for (Requires requires : desc.requires())
            {
                if (requireBundleHeader.length() > 0)
                {
                    requireBundleHeader.append(", ");
                }

                // before requiring based on the name make sure the required
                // module has a BSN that is the same
                String requiresBSN = getRequiresBSN(requires.name());
                requireBundleHeader.append(requiresBSN).append("; ");
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
            capabilities.append(
                JavaServiceNamespace.CAPABILITY_PROVIDES_WITH_ATTRIBUTE).append(
                    "=\"").append(String.join(",", provides.providers())).append("\"");
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

    private String getRequiresBSN(String name)
    {
        return module.getLayer().findModule(name).map(
            m -> atomosLayer.getAtomosContent(m).getSymbolicName()).orElse(name);

    }


    class ModuleConnectEntry implements ConnectEntry
    {
        final String name;
        final URI uri;

        public ModuleConnectEntry(String name, URI uri)
        {
            this.name = name;
            this.uri = uri;
        }

        @Override
        public long getContentLength()
        {
            try
            {
                return uri.toURL().openConnection().getContentLengthLong();
            }
            catch (IOException e)
            {
                return 0;
            }
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return currentReader().open(name).get();
        }

        @Override
        public long getLastModified()
        {
            try
            {
                return uri.toURL().openConnection().getDate();
            }
            catch (IOException e)
            {
                return 0;
            }
        }

        @Override
        public String getName()
        {
            return name;
        }

    }
}
