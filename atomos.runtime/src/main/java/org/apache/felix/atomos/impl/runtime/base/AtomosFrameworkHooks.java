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

import java.util.Collection;
import java.util.Iterator;

import org.apache.felix.atomos.runtime.AtomosContent;
import org.osgi.framework.Bundle;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

public class AtomosFrameworkHooks implements ResolverHookFactory, CollisionHook
{
    public class AtomosResolverHook implements ResolverHook
    {

        @Override
        public void filterResolvable(Collection<BundleRevision> candidates)
        {
            // do nothing
        }

        @Override
        public void filterSingletonCollisions(BundleCapability singleton,
            Collection<BundleCapability> collisionCandidates)
        {
            AtomosContent atomosBundle = atomosRuntime.getByConnectLocation(
                singleton.getRevision().getBundle().getLocation(), true);
            atomosRuntime.filterNotVisible(atomosBundle, collisionCandidates);
        }

        @Override
        public void filterMatches(BundleRequirement requirement,
            Collection<BundleCapability> candidates)
        {
            AtomosContent atomosBundle = atomosRuntime.getByConnectLocation(
                requirement.getRevision().getBundle().getLocation(), true);
            switch (requirement.getNamespace())
            {
                case PackageNamespace.PACKAGE_NAMESPACE:
                case BundleNamespace.BUNDLE_NAMESPACE:
                    atomosRuntime.filterBasedOnReadEdges(atomosBundle, candidates);
                    return;
                default:
                    atomosRuntime.filterNotVisible(atomosBundle, candidates);
            }

        }

        @Override
        public void end()
        {
            // do nothing
        }

    }

    final AtomosRuntimeBase atomosRuntime;

    AtomosFrameworkHooks(AtomosRuntimeBase atomosRuntime)
    {
        this.atomosRuntime = atomosRuntime;
    }

    @Override
    public ResolverHook begin(Collection<BundleRevision> triggers)
    {
        return new AtomosResolverHook();
    }

    @Override
    public void filterCollisions(int operationType, Bundle target,
        Collection<Bundle> collisionCandidates)
    {
        AtomosContent currentlyManaging = atomosRuntime.currentlyManagingConnected();
        if (currentlyManaging != null)
        {
            for (Iterator<Bundle> iCands = collisionCandidates.iterator(); iCands.hasNext();)
            {
                Bundle b = iCands.next();
                AtomosContent candidate = atomosRuntime.getConnectedContent(
                    b.getLocation());
                if (candidate != null)
                {
                    // Only other atomos connected contents can be filtered out
                    if (!atomosRuntime.isInLayerHierarchy(
                        currentlyManaging.getAtomosLayer(), candidate.getAtomosLayer()))
                    {
                        iCands.remove();
                    }
                }
            }
        }
    }
}
