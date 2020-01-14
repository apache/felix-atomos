/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Stefan Bischof  - gogo descriptors and checks
 *******************************************************************************/
package org.atomos.framework.base;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.service.command.Descriptor;
import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosLayer;
import org.atomos.framework.AtomosRuntime.LoaderType;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceRegistration;

public class AtomosCommands {

	public static String[] functions = new String[] { "list", "install", "uninstall" };
	private final AtomosRuntimeBase runtime;

	public AtomosCommands(AtomosRuntimeBase runtime) {
		this.runtime = runtime;
	}

	public ServiceRegistration<AtomosCommands> register(BundleContext context) {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put("osgi.command.function", functions);
		props.put("osgi.command.scope", "atomos");
		return context.registerService(AtomosCommands.class, this, props);
	}

	@Descriptor("List all layers")
	public void list() {
		final AtomosLayer bl = runtime.getBootLayer();
		layers(bl.getParents().stream().findFirst().orElseGet(() -> bl), new HashSet<>());
	}

	private void layers(AtomosLayer layer, Set<AtomosLayer> visited) {
		if (visited.add(layer)) {
			System.out.println(layer.toString());
			final Set<AtomosBundleInfo> bundles = layer.getAtomosBundles();
			if (!bundles.isEmpty()) {
				System.out.println(" BUNDLES:");
				for (final AtomosBundleInfo bundle : bundles) {
					final Bundle b = runtime.getBundle(bundle);
					System.out.println("  " + bundle.getSymbolicName() + getState(b));
				}
			}
			for (final AtomosLayer child : layer.getChildren()) {
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

	@Descriptor("Install a new layer")
	public void install(@Descriptor("Name of the layer") String name,
			@Descriptor("LoaderType of the layer [OSGI, SINGLE, MANY]") String loaderType,
			@Descriptor("Directory where the AtomosBundles are stored") File moduleDir)
					throws BundleException {

		if (!moduleDir.isDirectory()) {
			System.out.println("The specified path is not a directory: " + moduleDir.getAbsolutePath());
			return;
		}

		final Optional<LoaderType> oLoaderType = Stream.of(LoaderType.values())
				.filter(e -> e.name().equalsIgnoreCase(loaderType))
				.findAny();

		if (!oLoaderType.isPresent()) {
			final String v = Stream.of(LoaderType.values())
					.map(LoaderType::name)
					.collect(Collectors.joining(", "));
			System.out.printf("The specified loaderType is not valid. Use one of %s", v);
			return;
		}

		final AtomosLayer layer = runtime.addLayer(Collections.singletonList(runtime.getBootLayer()),
				name, oLoaderType.get(), moduleDir.toPath());

		final List<Bundle> bundles = new ArrayList<>();
		for (final AtomosBundleInfo atomosBundle : layer.getAtomosBundles()) {
			bundles.add(atomosBundle.install(null));
		}
		for (final Bundle b : bundles) {
			b.start();
		}
		layers(layer, new HashSet<>());
	}


	@Descriptor("Uninstall the layer with the given id")
	public void uninstall(@Descriptor("Id of the layer") long id) throws BundleException {

		final AtomosLayer layer = runtime.getById(id);
		if (layer == null) {
			System.out.println("No Atomos Layer with ID: " + id);
		} else {
			try {
				layer.uninstall();
				System.out.printf("Sucessfully uninstalled Atomos Layer \"%s\" with ID: %s",
						layer.getName(), id);
			} catch (final Exception e) {
				throw e;
			}
		}
	}
}
