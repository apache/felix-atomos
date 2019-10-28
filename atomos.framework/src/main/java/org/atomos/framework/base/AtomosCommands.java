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
package org.atomos.framework.base;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime.LoaderType;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceRegistration;

public class AtomosCommands {

	public String[] functions = new String[] {"list", "install", "uninstall"};
	private final AtomosRuntimeBase runtime;

	public AtomosCommands(AtomosRuntimeBase runtime) {
		this.runtime = runtime;
	}

	public ServiceRegistration<AtomosCommands> register(BundleContext context) {
		Hashtable<String, Object> props = new Hashtable<>();
		props.put("osgi.command.function", functions);
		props.put("osgi.command.scope", "atomos");
		return context.registerService(AtomosCommands.class, this, props);
	}

	public void list() {
		AtomosLayer bl = runtime.getBootLayer();
		layers(bl.getParents().stream().findFirst().orElseGet(() -> bl), new HashSet<>());
	}

	private void layers(AtomosLayer layer, Set<AtomosLayer> visited) {
		if (visited.add(layer)) {
			System.out.println(layer.toString());
			Set<AtomosBundleInfo> bundles = layer.getAtomosBundles();
			if (!bundles.isEmpty()) {
				System.out.println(" BUNDLES:");
				for (AtomosBundleInfo bundle : bundles) {
					Bundle b = runtime.getBundle(bundle);
					System.out.println("  " + bundle.getSymbolicName() + getState(b));
				}
			}
			for (AtomosLayer child : layer.getChildren()) {
				layers(child, visited);
			}
		}
	}

	private String getState(Bundle b) {
		if (b == null) {
			return " NOT_INSTALLED";
		}
		switch (b.getState()) {
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

	public void install(String name, String loaderType, File moduleDir) throws BundleException {
		if (!moduleDir.isDirectory()) {
			System.out.println("The specified path is not a directory: " + moduleDir.getAbsolutePath());
		}

		AtomosLayer layer = runtime.addLayer(Collections.singletonList(runtime.getBootLayer()), name, LoaderType.valueOf(loaderType), moduleDir.toPath());
		
		List<Bundle> bundles = new ArrayList<>();
		for (AtomosBundleInfo atomosBundle : layer.getAtomosBundles()) {
			bundles.add(atomosBundle.install(null));
		}
		for (Bundle b : bundles) {
			b.start();
		}
		layers(layer, new HashSet<>());
	}

	public void uninstall(long id) throws BundleException {
		AtomosLayer layer = runtime.getById(id);
		if (layer == null) {
			System.out.println("No Atomos Layer with ID: " + id);
		} else {
			layer.uninstall();
		}
	}
}
