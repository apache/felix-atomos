package org.atomos.framework.impl;

import org.eclipse.osgi.internal.hookregistry.FrameworkUtilHelper;
import org.osgi.framework.Bundle;

public class AtomosFrameworkUtilHelper extends FrameworkUtilHelper {
	static volatile AtomosRuntimeImpl atomosRuntime;
	@Override
	public Bundle getBundle(Class<?> classFromBundle) {
		AtomosRuntimeImpl current = atomosRuntime;
		if (current != null) {
			return atomosRuntime.getBundle(classFromBundle.getModule());
		}
		return null;
	}
}
