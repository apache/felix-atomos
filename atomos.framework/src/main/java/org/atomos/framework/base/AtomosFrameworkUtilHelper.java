/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.atomos.framework.base;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.osgi.framework.Bundle;
import org.osgi.framework.connect.FrameworkUtilHelper;

public class AtomosFrameworkUtilHelper implements FrameworkUtilHelper {
	static private final Set<FrameworkUtilHelper> helpers = new CopyOnWriteArraySet<>();

	static void addHelper(FrameworkUtilHelper helper) {
		helpers.add(helper);
	}
	static void removeHelper(FrameworkUtilHelper helper) {
		helpers.remove(helper);
	}

	@Override
	public Bundle getBundle(Class<?> classFromBundle) {
		for (FrameworkUtilHelper helper : helpers) {
			Bundle b = helper.getBundle(classFromBundle);
			if (b != null) {
				return b;
			}
		}
		return null;
	}
}
