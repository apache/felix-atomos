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

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceRegistration;

public class AtomosCommands {

	public String[] functions = new String[] {"list", "install"};
	private final AtomosRuntimeImpl runtime;

	public AtomosCommands(AtomosRuntimeImpl runtime) {
		this.runtime = runtime;
	}

	public ServiceRegistration<AtomosCommands> register(BundleContext context) {
		Hashtable<String, Object> props = new Hashtable<>();
		props.put("osgi.command.function", functions);
		props.put("osgi.command.scope", "atomos");
		return context.registerService(AtomosCommands.class, this, props);
	}

	public void list() {
		layers(runtime.getBootLayer(), new HashSet<>());
	}

	private void layers(AtomosLayer layer, Set<AtomosLayer> visited) {
		if (visited.add(layer)) {
			System.out.println(layer.getName());
			for (AtomosLayer child : layer.getChildren()) {
				layers(child, visited);
			}
		}
	}

	public void install(String name, File moduleDir) throws BundleException {
		if (!moduleDir.isDirectory()) {
			System.out.println("The specified path is not a directory: " + moduleDir.getAbsolutePath());
		}

		AtomosLayer layer = runtime.addLayer(List.of(runtime.getBootLayer()), name, moduleDir.toPath());
		layers(layer, new HashSet<>());
		
		List<Bundle> bundles = new ArrayList<>();
		for (AtomosBundleInfo atomosBundle : layer.getAtomosBundles()) {
			bundles.add(atomosBundle.install(null));
		}
		for (Bundle b : bundles) {
			b.start();
		}
	}
}
