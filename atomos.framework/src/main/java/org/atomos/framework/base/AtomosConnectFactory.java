package org.atomos.framework.base;

import java.io.File;
import java.util.Map;
import java.util.Optional;

import org.atomos.framework.AtomosBundleInfo;
import org.atomos.framework.AtomosRuntime;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.connect.ConnectFactory;
import org.osgi.framework.connect.ConnectModule;

public class AtomosConnectFactory implements ConnectFactory{
	final AtomosRuntimeBase atomosRuntime;

	public AtomosConnectFactory() {
		this(null);
	}
	public AtomosConnectFactory(AtomosRuntime atomosRuntime) {
		if (atomosRuntime == null) {
			atomosRuntime = AtomosRuntime.newAtomosRuntime();
		}
		this.atomosRuntime = (AtomosRuntimeBase) atomosRuntime;
	}

	@Override
	public Optional<BundleActivator> createBundleActivator() {
		return Optional.of(new BundleActivator() {
			@Override
			public void start(BundleContext bc) throws Exception {
				atomosRuntime.start(bc);
			}
			@Override
			public void stop(BundleContext bc) throws Exception {
				atomosRuntime.stop(bc);
			}
		});
	}

	@Override
	public Optional<ConnectModule> getModule(String location) {
		final AtomosBundleInfo atomosBundle = atomosRuntime.getAtomosBundle(location);
		if (atomosBundle == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(() -> atomosBundle.getConnectContent());
		
	}

	@Override
	public void initialize(File storage, Map<String, String> configuration) {
		atomosRuntime.initialize(storage, configuration);
	}

}
