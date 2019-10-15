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
package org.atomos.service.impl.activator;

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.atomos.service.contract.Echo;

@org.osgi.annotation.bundle.Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
@org.osgi.annotation.bundle.Requirement(namespace = "osgi.ee", filter = "(&(osgi.ee=JavaSE)(version=1.8))")
public class Activator implements BundleActivator {

	@Override
	public void start(BundleContext context) throws Exception {
		Echo impl = (m) -> "impl.activator " + m;
		context.registerService(Echo.class, impl, new Hashtable<String, String>(Collections.singletonMap("type", "impl.activator")));
		System.out.println("Registered Echo service from activator.");
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		// Do nothing; unregistration happens automatically
	}

}
