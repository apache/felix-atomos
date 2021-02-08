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
import java.util.Map;
import java.util.Optional;

import org.apache.felix.atomos.Atomos;
import org.apache.felix.atomos.impl.base.AtomosBase.AtomosLayerBase.AtomosContentBase;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.connect.ConnectModule;
import org.osgi.framework.connect.ModuleConnector;

public class AtomosModuleConnector implements ModuleConnector
{
    final AtomosBase atomos;

    public AtomosModuleConnector()
    {
        this(null);
    }

    public AtomosModuleConnector(AtomosBase atomos)
    {
        if (atomos == null)
        {
            atomos = (AtomosBase) Atomos.newAtomos();
        }
        this.atomos = atomos;
    }

    @Override
    public Optional<BundleActivator> newBundleActivator()
    {
        atomos.debug("Creating Atomos activator");
        return Optional.of(new BundleActivator()
        {
            @Override
            public void start(BundleContext bc) throws Exception
            {
                atomos.start(bc);
            }

            @Override
            public void stop(BundleContext bc) throws Exception
            {
                atomos.stop(bc);
            }
        });
    }

    @Override
    public Optional<ConnectModule> connect(String location)
    {
        atomos.debug("Framework is attempting to connect location: %s", location);

        final AtomosContentBase atomosBundle = atomos.getByConnectLocation(
            location, false);
        if (atomosBundle == null)
        {
            return Optional.empty();
        }
        atomos.addManagingConnected(atomosBundle, location);
        return Optional.of(atomosBundle::getConnectContent);

    }

    @Override
    public void initialize(File storage, Map<String, String> configuration)
    {
        atomos.initialize(storage, configuration);
    }

}
