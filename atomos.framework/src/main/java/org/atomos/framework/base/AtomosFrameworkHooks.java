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
package org.atomos.framework.base;

import java.util.Collection;
import java.util.Iterator;

import org.atomos.framework.AtomosBundleInfo;
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
            AtomosBundleInfo atomosBundle = atomosRuntime.getByOSGiLocation(
                singleton.getRevision().getBundle().getLocation());
            atomosRuntime.filterNotVisible(atomosBundle, collisionCandidates);
        }

        @Override
        public void filterMatches(BundleRequirement requirement,
            Collection<BundleCapability> candidates)
        {
            AtomosBundleInfo atomosBundle = atomosRuntime.getByOSGiLocation(
                requirement.getRevision().getBundle().getLocation());
            switch (requirement.getNamespace())
            {
                case PackageNamespace.PACKAGE_NAMESPACE:
                case BundleNamespace.BUNDLE_NAMESPACE:
                    atomosRuntime.filterBasedOnReadEdges(atomosBundle, candidates);
                    return;
                default:
                    atomosRuntime.filterNotVisible(atomosBundle, candidates);
                    return;
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
        AtomosBundleInfo currentlyInstalling = atomosRuntime.currentlyInstalling();
        if (currentlyInstalling != null)
        {
            for (Iterator<Bundle> iCands = collisionCandidates.iterator(); iCands.hasNext();)
            {
                Bundle b = iCands.next();
                AtomosBundleInfo candidate = atomosRuntime.getAtomosBundle(
                    b.getLocation());
                if (candidate != null)
                {
                    // Only other atomos bundles can be filtered out
                    if (!atomosRuntime.isInLayerHierarchy(
                        currentlyInstalling.getAtomosLayer(), candidate.getAtomosLayer()))
                    {
                        iCands.remove();
                    }
                }
            }
        }
    }
}
