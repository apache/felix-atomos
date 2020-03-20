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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase.AtomosLayerBase;
import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase.AtomosLayerBase.AtomosContentBase;
import org.apache.felix.atomos.runtime.AtomosContent;
import org.apache.felix.atomos.runtime.AtomosLayer;
import org.apache.felix.atomos.runtime.AtomosLayer.LoaderType;
import org.osgi.framework.Constants;

public class AtomosStorage
{
    private final static int VERSION = 1;
    private final static String ATOMOS_STORE = "atomosStore.data";
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
            atomosRuntime.debug("Found %s in %s", ATOMOS_STORE, root);
            int persistentVersion = in.readInt();
            if (persistentVersion > VERSION)
            {
                throw new IOException(
                    "Atomos persistent version is greater than supported version: "
                        + VERSION + "<" + persistentVersion);
            }
            long nextLayerId = in.readLong();
            int numLayers = in.readInt();
            if (numLayers > 1 && !atomosRuntime.getBootLayer().isAddLayerSupported())
            {
                System.out.println(
                    "Atomos persistent layers are ignored because Atomos is not loaded as a module.");
                return;
            }
            for (int i = 0; i < numLayers; i++)
            {
                readLayer(in);
            }
            atomosRuntime.nextLayerId.set(nextLayerId);
        }
        catch (FileNotFoundException e)
        {
            // ignore no file
            atomosRuntime.debug("No %s found in %s", ATOMOS_STORE, root);
        }
        finally
        {
            atomosRuntime.unlockWrite();
        }
    }

    void saveLayers(File root) throws IOException
    {
        File atomosStore = new File(root, ATOMOS_STORE);
        atomosRuntime.lockRead();
        try (DataOutputStream out = new DataOutputStream(
            new BufferedOutputStream(new FileOutputStream(atomosStore))))
        {
            out.writeInt(VERSION);
            out.writeLong(atomosRuntime.nextLayerId.get());
            List<AtomosLayerBase> writeOrder = getLayerWriteOrder(
                atomosRuntime.getBootLayer(), new HashSet<>(),
                new ArrayList<>());
            out.writeInt(writeOrder.size());
            for (AtomosLayerBase layer : writeOrder)
            {
                writeLayer(layer, out);
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
        atomosRuntime.debug("Loading layer %s %s %s", name, id, loaderType);

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
            String atomosLocation = in.readUTF();
            atomosRuntime.debug("Found Atomos location %s", atomosLocation);
            if (in.readBoolean())
            {
                String connectLocation = in.readUTF();
                atomosRuntime.debug("Found connected location %s", connectLocation);
                if (Constants.SYSTEM_BUNDLE_LOCATION.equals(connectLocation))
                {
                    // don't do anything for the system bundle, it is already connected
                    continue;
                }
                AtomosContentBase atomosContent = atomosRuntime.getByAtomosLocation(
                    atomosLocation);
                if (atomosContent != null)
                {
                    atomosRuntime.connectAtomosContent(connectLocation, atomosContent);
                }
                else
                {
                    atomosRuntime.debug("Unable to find atomos content for location %s",
                        atomosLocation);
                }
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
            out.writeLong(parent.getId());
        }

        Set<AtomosContent> contents = layer.getAtomosContents();
        out.writeInt(contents.size());
        for (AtomosContent content : contents)
        {
            String atomosLocation = content.getAtomosLocation();
            out.writeUTF(atomosLocation);
            String connectLocation = content.getConnectLocation();
            out.writeBoolean(connectLocation != null);
            if (connectLocation != null)
            {
                out.writeUTF(connectLocation);
            }
        }
    }

}
