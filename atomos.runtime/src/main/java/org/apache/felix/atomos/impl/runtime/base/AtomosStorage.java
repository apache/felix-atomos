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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase.AtomosLayerBase;
import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase.AtomosLayerBase.AtomosBundleInfoBase;
import org.apache.felix.atomos.runtime.AtomosBundleInfo;
import org.apache.felix.atomos.runtime.AtomosLayer;
import org.apache.felix.atomos.runtime.AtomosRuntime.LoaderType;
import org.osgi.framework.Bundle;

public class AtomosStorage
{
    private final static int VERSION = 1;
    private final String ATOMOS_STORE = "atomosStore.data";
    private final AtomosRuntimeBase atomosRuntime;

    public AtomosStorage(AtomosRuntimeBase atomosRuntime)
    {
        this.atomosRuntime = atomosRuntime;
    }

    void loadLayers(File root) throws IOException
    {
        atomosRuntime.lockWrite();
        try (DataInputStream in = new DataInputStream(
            new BufferedInputStream(new FileInputStream(new File(root, ATOMOS_STORE)))))
        {
            int persistentVersion = in.readInt();
            if (persistentVersion > VERSION)
            {
                throw new IOException(
                    "Atomos persistent version is greater than supported version: "
                        + VERSION + "<" + persistentVersion);
            }
            long nextLayerId = in.readLong();
            int numLayers = in.readInt();
            for (int i = 0; i < numLayers; i++)
            {
                readLayer(in);
            }
            atomosRuntime.nextLayerId.set(nextLayerId);
        }
        catch (FileNotFoundException e)
        {
            // ignore no file
        }
        finally
        {
            atomosRuntime.unlockWrite();
        }
    }

    void saveLayers(File root, Bundle[] bundles) throws IOException
    {
        File atomosStore = new File(root, ATOMOS_STORE);
        atomosRuntime.lockRead();
        try (DataOutputStream out = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(atomosStore))))
        {
            out.writeInt(VERSION);
            out.writeLong(atomosRuntime.nextLayerId.get());
            List<AtomosLayerBase> writeOrder = getLayerWriteOrder(
                (AtomosLayerBase) atomosRuntime.getBootLayer(), new HashSet<>(),
                new ArrayList<>());
            out.writeInt(writeOrder.size());
            for (AtomosLayerBase layer : writeOrder)
            {
                writeLayer(layer, out);
            }

            out.writeInt(bundles.length);
            for (Bundle b : bundles)
            {
                String osgiLocation = b.getLocation();
                AtomosBundleInfo atomosBundle = atomosRuntime.getByOSGiLocation(
                    osgiLocation);
                if (atomosBundle != null)
                {
                    out.writeBoolean(true);
                    out.writeUTF(osgiLocation);
                    out.writeUTF(atomosBundle.getLocation());
                }
                else
                {
                    out.writeBoolean(false);
                }
            }
        }
        finally
        {
            atomosRuntime.unlockRead();
        }
    }

    private List<AtomosLayerBase> getLayerWriteOrder(AtomosLayer layer,
        Set<AtomosLayer> visited, List<AtomosLayerBase> result)
    {
        if (!visited.add(layer))
        {
            return result;
        }

        // visit all parents first
        for (AtomosLayer parent : layer.getParents())
        {
            getLayerWriteOrder(parent, visited, result);
        }

        // add self before children
        result.add((AtomosLayerBase) layer);

        // now visit children
        for (AtomosLayer child : layer.getChildren())
        {
            getLayerWriteOrder(child, visited, result);
        }
        return result;
    }

    private void readLayer(DataInputStream in) throws IOException
    {
        String name = in.readUTF();
        long id = in.readLong();
        LoaderType loaderType = LoaderType.valueOf(in.readUTF());
        int numPaths = in.readInt();
        Path[] paths = new Path[numPaths];
        for (int i = 0; i < numPaths; i++)
        {
            String sURI = in.readUTF();
            try
            {
                URI uri = new URI(sURI);
                // TODO on Java 11 should use Path.of()
                paths[i] = new File(uri).toPath();
            }
            catch (URISyntaxException e)
            {
                throw new IOException(e);
            }
        }
        int numParents = in.readInt();
        List<AtomosLayer> parents = new ArrayList<>();
        for (int i = 0; i < numParents; i++)
        {
            long parentId = in.readLong();
            AtomosLayerBase parent = atomosRuntime.getById(parentId);
            if (parent == null)
            {
                throw new IllegalArgumentException("Missing parent with id: " + parentId);
            }
            parents.add(parent);
        }
        if (atomosRuntime.getById(id) == null)
        {
            try
            {
                atomosRuntime.addLayer(parents, name, id, loaderType, paths);
            }
            catch (Exception e)
            {
                throw new IllegalArgumentException(
                    "Error adding persistent layer: " + e.getMessage());
            }
        }

        int numBundles = in.readInt();
        for (int i = 0; i < numBundles; i++)
        {
            String osgiLocation = in.readUTF();
            String atomosLocation = in.readUTF();
            AtomosBundleInfoBase atomosBundle = atomosRuntime.getByAtomosLocation(
                atomosLocation);
            if (atomosBundle != null)
            {
                atomosRuntime.addToInstalledBundles(osgiLocation, atomosBundle);
            }
        }
    }

    private void writeLayer(AtomosLayerBase layer, DataOutputStream out)
        throws IOException
    {
        out.writeUTF(layer.getName());
        out.writeLong(layer.getId());
        out.writeUTF(layer.getLoaderType().toString());
        List<Path> paths = layer.getPaths();
        out.writeInt(paths.size());
        for (Path path : paths)
        {
            out.writeUTF(path.toUri().toString());
        }
        List<AtomosLayer> parents = layer.getParents();
        out.writeInt(parents.size());
        for (AtomosLayer parent : parents)
        {
            out.writeLong(((AtomosLayerBase) parent).getId());
        }
        Collection<String> installedLocations = atomosRuntime.getInstalledLocations(
            layer);
        out.writeInt(installedLocations.size());
        for (String osgiLocation : installedLocations)
        {
            AtomosBundleInfoBase atomosBundle = atomosRuntime.getByOSGiLocation(
                osgiLocation);
            String atomosLocation = atomosBundle.getLocation();
            out.writeUTF(osgiLocation);
            out.writeUTF(atomosLocation);
        }
    }

}
