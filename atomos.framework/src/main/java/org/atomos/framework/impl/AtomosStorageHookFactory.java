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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.container.ModuleRevisionBuilder;
import org.eclipse.osgi.container.ModuleContainerAdaptor.ModuleEvent;
import org.eclipse.osgi.container.ModuleRevisionBuilder.GenericInfo;
import org.eclipse.osgi.framework.log.FrameworkLogEntry;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.internal.hookregistry.StorageHookFactory;
import org.eclipse.osgi.internal.hookregistry.StorageHookFactory.StorageHook;
import org.eclipse.osgi.storage.BundleInfo.Generation;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Namespace;
import org.atomos.framework.AtomosBundleInfo;

public class AtomosStorageHookFactory extends StorageHookFactory<Object, Object, StorageHook<Object, Object>> {
	private final AtomosRuntimeImpl atomosRuntime;
	private final HookRegistry hookRegistry;
	
	public AtomosStorageHookFactory(AtomosRuntimeImpl atomosRuntime, HookRegistry hookRegistry) {
		this.atomosRuntime = atomosRuntime;
		this.hookRegistry = hookRegistry;
	}

	@Override
	public int getStorageVersion() {
		return 1;
	}

	@Override
	protected StorageHook<Object, Object> createStorageHook(Generation generation) {
		return new StorageHook<Object, Object>(generation, this.getClass()) {

			@Override
			public void initialize(Dictionary<String, String> manifest) throws BundleException {
				// nothing
			}

			@Override
			public void load(Object loadContext, DataInputStream is) throws IOException {
				if (is.readBoolean()) {
					String atomosLocation = is.readUTF();
					AtomosBundleInfoImpl atomosBundle = atomosRuntime.getByAtomLocation(atomosLocation);
					if (atomosBundle != null) {
						String osgiLocation = generation.getBundleInfo().getLocation();
						int firstColon = osgiLocation.indexOf(':');
						if (firstColon >= 0) {
							if (atomosLocation.equals(osgiLocation.substring(firstColon + 1))) {
								atomosRuntime.addToInstalledBundles(osgiLocation, atomosBundle);
								return;
							}
						}
					}
					// We throw an IllegalArgumentException to force a clean start.
					// NOTE this is really depends on an internal of the framework.
					throw new IllegalArgumentException();
				}
			}

			@Override
			public void save(Object saveContext, DataOutputStream os) throws IOException {
				String location = generation.getBundleInfo().getLocation();
				AtomosBundleInfo bootBundle = atomosRuntime.getByOSGiLocation(location);
				if (bootBundle != null) {
					os.writeBoolean(true);
					os.writeUTF(bootBundle.getLocation());
				} else {
					os.writeBoolean(false);
				}
			}

			@Override
			public ModuleRevisionBuilder adaptModuleRevisionBuilder(ModuleEvent operation,
					org.eclipse.osgi.container.Module origin, ModuleRevisionBuilder builder) {
				Generation generation = getGeneration();
				AtomosBundleInfo atomosBundle = atomosRuntime.getByOSGiLocation(generation.getBundleInfo().getLocation());
				if (atomosBundle != null) {
					return createBuilder(atomosBundle, builder, hookRegistry);
				}
				return super.adaptModuleRevisionBuilder(operation, origin, builder);
			}
		};
	}

	ModuleRevisionBuilder createBuilder(AtomosBundleInfo atomosBundle, ModuleRevisionBuilder original,
			HookRegistry hookRegistry) {

		boolean origHasBSN = original.getSymbolicName() != null;

		if (origHasBSN) {
			if (!original.getSymbolicName().equals(atomosBundle.getSymbolicName())) {
				hookRegistry.getContainer().getLogServices().log(AtomosRuntimeImpl.thisModule.getName(), FrameworkLogEntry.ERROR,
						"Bundle symbolic name does not match the module name: " + original.getSymbolicName() + " vs "
								+ atomosBundle.getSymbolicName(),
						null);
			}
		}

		List<GenericInfo> origCaps = original.getCapabilities();
		List<GenericInfo> origReqs = original.getRequirements();

		ModuleDescriptor desc = atomosBundle.getResolvedModule().reference().descriptor();
		ModuleRevisionBuilder builder = new ModuleRevisionBuilder();

		if (origHasBSN) {
			builder.setSymbolicName(original.getSymbolicName());
			builder.setVersion(original.getVersion());
			builder.setTypes(original.getTypes());
		} else {
			builder.setSymbolicName(desc.name());
			Version version = desc.version().map((v) -> {
				try {
					return Version.valueOf(v.toString());
				} catch (IllegalArgumentException e) {
					return Version.emptyVersion;
				}
			}).orElse(Version.emptyVersion);
			builder.setVersion(version);

			// add bundle and identity capabilities, do not create host capability for JPMS
			builder.addCapability(BundleNamespace.BUNDLE_NAMESPACE, Map.of(), Map.of(BundleNamespace.BUNDLE_NAMESPACE,
					desc.name(), BundleNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE, version));
			builder.addCapability(IdentityNamespace.IDENTITY_NAMESPACE, Map.of(),
					Map.of(IdentityNamespace.IDENTITY_NAMESPACE, desc.name(),
							IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE, version));

			// only do exports for non bundle modules
			// real OSGi bundles already have good export capabilities
			for (Exports exports : desc.exports()) {
				// TODO map targets to x-friends directive.
				builder.addCapability(PackageNamespace.PACKAGE_NAMESPACE, Map.of(),
						Map.of(PackageNamespace.PACKAGE_NAMESPACE, exports.source()));
			}
		}

		// map provides to a made up namespace only to give proper resolution errors
		// (although JPMS will likely complain first
		for (Provides provides : desc.provides()) {
			builder.addCapability(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE, Map.of(),
					Map.of(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE, provides.service(),
							JavaServiceNamespace.CAPABILITY_PROVIDES_WITH, provides.providers()));
		}

		// map requires to require bundle
		for (Requires requires : desc.requires()) {
			Map<String, String> directives = new HashMap<>();

			// determine the resolution value based on the STATIC modifier
			String resolution = requires.modifiers().contains(Requires.Modifier.STATIC) ? Namespace.RESOLUTION_OPTIONAL
					: Namespace.RESOLUTION_MANDATORY;
			directives.put(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, resolution);
			// determine the visibility value based on the TRANSITIVE modifier
			String visibility = requires.modifiers().contains(Requires.Modifier.TRANSITIVE)
					? BundleNamespace.VISIBILITY_REEXPORT
					: BundleNamespace.VISIBILITY_PRIVATE;
			directives.put(BundleNamespace.REQUIREMENT_VISIBILITY_DIRECTIVE, visibility);
			// create a bundle filter based on the requires name
			directives.put(Namespace.REQUIREMENT_FILTER_DIRECTIVE,
					"(" + BundleNamespace.BUNDLE_NAMESPACE + "=" + requires.name() + ")");

			builder.addRequirement(BundleNamespace.BUNDLE_NAMESPACE, directives, Collections.emptyMap());
		}

		// map uses to a made up namespace only to give proper resolution errors
		// (although JPMS will likely complain first)
		for (String uses : desc.uses()) {
			builder.addRequirement(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE,
					Map.of(Namespace.REQUIREMENT_RESOLUTION_DIRECTIVE, Namespace.RESOLUTION_OPTIONAL,
							Namespace.REQUIREMENT_FILTER_DIRECTIVE,
							"(" + JavaServiceNamespace.JAVA_SERVICE_NAMESPACE + "=" + uses + ")"),
					Map.of(JavaServiceNamespace.JAVA_SERVICE_NAMESPACE, uses));
		}

		origCaps.forEach((c) -> {
			Map<String, String> directives = c.getDirectives();
			if (directives.containsKey(Namespace.CAPABILITY_USES_DIRECTIVE)) {
				directives = new HashMap<>(directives);
				directives.remove(Namespace.CAPABILITY_USES_DIRECTIVE);
			}
			builder.addCapability(c.getNamespace(), directives, c.getAttributes());
		});
		origReqs.forEach((r) -> builder.addRequirement(r.getNamespace(), r.getDirectives(), r.getAttributes()));

		return builder;
	}
}
