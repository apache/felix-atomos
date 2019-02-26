package org.atomos.framework.base;

import org.eclipse.osgi.internal.hookregistry.FrameworkUtilHelper;
import org.osgi.framework.Bundle;

public class AtomosFrameworkUtilHelper extends FrameworkUtilHelper {
	static volatile AtomosRuntimeBase atomosRuntime;
	@Override
	public Bundle getBundle(Class<?> classFromBundle) {
		AtomosRuntimeBase current = atomosRuntime;
		if (current != null) {
			return atomosRuntime.getBundle(classFromBundle);
		}
		return null;
	}
}
