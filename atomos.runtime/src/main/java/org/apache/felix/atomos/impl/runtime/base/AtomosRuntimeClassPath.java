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

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.felix.atomos.runtime.AtomosContent;
import org.apache.felix.atomos.runtime.AtomosLayer;
import org.apache.felix.atomos.runtime.AtomosLayer.LoaderType;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.wiring.BundleCapability;

public class AtomosRuntimeClassPath extends AtomosRuntimeBase
{

    private final AtomosLayer bootLayer = createBootLayer();

    private AtomosLayer createBootLayer()
    {
        lockWrite();
        try
        {
            AtomosLayerBase result = new AtomosLayerClassPath(Collections.emptyList(),
                nextLayerId.getAndIncrement(), "boot", LoaderType.SINGLE);
            addAtomosLayer(result);
            return result;
        }
        finally
        {
            unlockWrite();
        }
    }

    @Override
    protected AtomosLayer addLayer(List<AtomosLayer> parents, String name, long id,
        LoaderType loaderType, Path... paths)
    {
        throw new UnsupportedOperationException(
            "Cannot add module layers when Atomos is not loaded as module.");
    }

    @Override
    public ConnectFrameworkFactory findFrameworkFactory()
    {
        Iterator<ConnectFrameworkFactory> itr = ServiceLoader.load(
            ConnectFrameworkFactory.class, getClass().getClassLoader()).iterator();
        if (itr.hasNext())
        {
            return itr.next();
        }
        throw new RuntimeException("No Framework implementation found.");
    }

    @Override
    protected void filterBasedOnReadEdges(AtomosContent atomosContent,
        Collection<BundleCapability> candidates)
    {
        filterNotVisible(atomosContent, candidates);
    }

    public class AtomosLayerClassPath extends AtomosLayerBase
    {
        private final Set<AtomosContentBase> atomosContents;

        protected AtomosLayerClassPath(List<AtomosLayer> parents, long id, String name, LoaderType loaderType, Path... paths)
        {
            super(parents, id, name, loaderType, paths);
            atomosContents = findClassPathAtomosContents();
        }

        @Override
        public final Set<AtomosContent> getAtomosContents()
        {
            return asSet(atomosContents);
        }

        @Override
        protected void findBootLayerAtomosContents(Set<AtomosContentBase> result)
        {
            // do nothing for class path runtime case
        }
    }

    @Override
    public AtomosLayer getBootLayer()
    {
        return bootLayer;
    }

    @Override
    protected void addingLayer(AtomosLayerBase atomosLayer)
    {
        // nothing to do
    }

    @Override
    protected void removedLayer(AtomosLayerBase atomosLayer)
    {
        // nothing to do
    }
}
