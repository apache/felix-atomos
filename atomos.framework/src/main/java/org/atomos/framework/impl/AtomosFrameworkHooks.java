/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.atomos.framework.impl;

import java.util.Collection;
import java.util.Iterator;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.osgi.framework.Bundle;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.hooks.resolver.ResolverHook;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

public class AtomosFrameworkHooks implements ResolverHookFactory, CollisionHook {
	public class AtomosResolverHook implements ResolverHook {

		@Override
		public void filterResolvable(Collection<BundleRevision> candidates) {
			// do nothing
		}

		@Override
		public void filterSingletonCollisions(BundleCapability singleton,
				Collection<BundleCapability> collisionCandidates) {
			AtomosBundleInfo atomosBundle = atomosRuntime.getByOSGiLocation(singleton.getRevision().getBundle().getLocation());
			if (atomosBundle != null) {
				// only filter collisions for atomos bundles; normal bundles have normal collision rules
				for (Iterator<BundleCapability> iCands = collisionCandidates.iterator(); iCands.hasNext();) {
					BundleCapability candidate = iCands.next();
					if (!isVisible(atomosBundle, candidate)) {
						iCands.remove();
					}
				}
			}
		}

		@Override
		public void filterMatches(BundleRequirement requirement, Collection<BundleCapability> candidates) {
			AtomosBundleInfo atomosBundle = atomosRuntime.getByOSGiLocation(requirement.getRevision().getBundle().getLocation());
			if (atomosBundle != null) {
				for (Iterator<BundleCapability> iCands = candidates.iterator(); iCands.hasNext();) {
					BundleCapability candidate = iCands.next();
					if (!isVisible(atomosBundle, candidate)) {
						iCands.remove();
					}
				}
			}
		}

		@Override
		public void end() {
			// do nothing
		}

	}
	final AtomosRuntimeImpl atomosRuntime;
	AtomosFrameworkHooks(AtomosRuntimeImpl atomosRuntime) {
		this.atomosRuntime = atomosRuntime;
	}
	@Override
	public ResolverHook begin(Collection<BundleRevision> triggers) {
		return new AtomosResolverHook();
	}
	@Override
	public void filterCollisions(int operationType, Bundle target, Collection<Bundle> collisionCandidates) {
		AtomosBundleInfo currentlyInstalling = atomosRuntime.currentlyInstalling();
		if (currentlyInstalling != null) {
			for (Iterator<Bundle> iCands = collisionCandidates.iterator(); iCands.hasNext();) {
				Bundle b = iCands.next();
				AtomosBundleInfo candidate = atomosRuntime.getAtomosBundle(b.getLocation());
				if (candidate != null) {
					// Only other atomos bundles can be filtered out
					if (!isInLayerHierarchy(currentlyInstalling.getAtomosLayer(), candidate.getAtomosLayer())) {
						iCands.remove();
					}
				}
			}
		}
	}

	boolean isVisible(AtomosBundleInfo atomosBundle, BundleCapability candidate) {
		AtomosBundleInfo candidateAtomos = atomosRuntime.getByOSGiLocation(candidate.getRevision().getBundle().getLocation());
		if (candidateAtomos == null) {
			// atomos bundles cannot see normal bundles
			return false;
		} else {
			AtomosLayer thisLayer = atomosBundle.getAtomosLayer();
			return isInLayerHierarchy(thisLayer, candidateAtomos.getAtomosLayer());				
		}
	}

	private boolean isInLayerHierarchy(AtomosLayer thisLayer, AtomosLayer candLayer) {
		if (thisLayer.equals(candLayer)) {
			return true;
		}
		for (AtomosLayer parent : thisLayer.getParents()) {
			if (isInLayerHierarchy(parent, candLayer)) {
				return true;
			}
		}
		return false;
	}
}
