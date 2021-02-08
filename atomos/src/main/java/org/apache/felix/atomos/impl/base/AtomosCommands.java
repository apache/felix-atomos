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
package org.apache.felix.atomos.impl.base;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.AtomosContent;
import org.apache.felix.atomos.AtomosLayer;
import org.apache.felix.atomos.AtomosLayer.LoaderType;
import org.apache.felix.service.command.Descriptor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceRegistration;

public class AtomosCommands
{

    public static String[] functions = new String[] { "list", "install", "uninstall" };
    private final AtomosBase runtime;

    public AtomosCommands(AtomosBase runtime)
    {
        this.runtime = runtime;
    }

    public ServiceRegistration<AtomosCommands> register(BundleContext context)
    {
        Hashtable<String, Object> props = new Hashtable<>();
        props.put("osgi.command.function", functions);
        props.put("osgi.command.scope", "atomos");
        return context.registerService(AtomosCommands.class, this, props);
    }

    @Descriptor("List all layers")
    public void list()
    {
        AtomosLayer bl = runtime.getBootLayer();
        layers(bl.getParents().stream().findFirst().orElse(bl), new HashSet<>());
    }

    private void layers(AtomosLayer layer, Set<AtomosLayer> visited)
    {
        if (visited.add(layer))
        {
            System.out.println(layer.toString());
            Set<AtomosContent> contents = layer.getAtomosContents();
            if (!contents.isEmpty())
            {
                System.out.println(" BUNDLES:");
                for (AtomosContent content : contents)
                {
                    Bundle b = runtime.getBundle(content);
                    System.out.println("  " + content.getSymbolicName() + getState(b));
                }
            }
            for (AtomosLayer child : layer.getChildren())
            {
                layers(child, visited);
            }
        }
    }

    private String getState(Bundle b)
    {
        if (b == null)
        {
            return " NOT_INSTALLED";
        }
        switch (b.getState())
        {
            case Bundle.INSTALLED:
                return " INSTALLED";
            case Bundle.RESOLVED:
                return " RESOLVED";
            case Bundle.STARTING:
                return " STARTING";
            case Bundle.ACTIVE:
                return " ACTIVE";
            case Bundle.STOPPING:
                return " STOPPING";
            case Bundle.UNINSTALLED:
                return " UNINSTALLED";
            default:
                return " UNKNOWN";
        }
    }

    @Descriptor("Install a new layer")
    public void install(@Descriptor("Name of the layer") String name,
        @Descriptor("LoaderType of the layer [OSGI, SINGLE, MANY]") String loaderType,
        @Descriptor("Directory containing bundle JARs to install into the layer") File moduleDir)
        throws BundleException
    {
        if (!moduleDir.isDirectory())
        {
            System.out.println(
                "The specified path is not a directory: " + moduleDir.getAbsolutePath());
            return;
        }

        Optional<LoaderType> oLoaderType = Stream.of(LoaderType.values()).filter(
            e -> e.name().equalsIgnoreCase(loaderType)).findAny();

        if (oLoaderType.isEmpty())
        {
            String v = Stream.of(LoaderType.values()).map(
                LoaderType::name).collect(
                Collectors.joining(", "));
            System.out.printf("The specified loaderType is not valid. Use one of %s", v);
            return;
        }

        AtomosLayer layer = runtime.getBootLayer().addLayer(name, oLoaderType.get(),
            moduleDir.toPath());

        List<Bundle> bundles = new ArrayList<>();
        for (final AtomosContent atomosBundle : layer.getAtomosContents())
        {
            bundles.add(atomosBundle.install());
        }
        for (final Bundle b : bundles)
        {
            b.start();
        }
        layers(layer, new HashSet<>());
    }

    @Descriptor("Uninstall the layer with the given id")
    public void uninstall(@Descriptor("Id of the layer") long id) throws BundleException
    {
        AtomosLayer layer = runtime.getById(id);
        if (layer == null)
        {
            System.out.println("No Atomos Layer with ID: " + id);
        }
        else
        {
            try
            {
                layer.uninstall();
                System.out.printf(
                    "Sucessfully uninstalled Atomos Layer \"%s\" with ID: %s",
                    layer.getName(), id);
            }
            catch (Exception e)
            {
                System.out.printf(
                    "Failing to uninstall this Atomos Layer \"%s\" with ID: %s",
                    layer.getName(), id);
                throw e;
            }
        }
    }
}
