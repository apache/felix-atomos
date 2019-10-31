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
package org.atomos.service.impl;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.atomos.service.contract.Echo;

@Component(property = {"type=impl.component"}, immediate = true)
@org.osgi.annotation.bundle.Requirement(namespace = "osgi.ee", filter = "(&(osgi.ee=JavaSE)(version=1.8))")
public class EchoImpl implements Echo {

	@Activate
	public void activate() {
		System.out.println("Activated: " + getClass().getName());
	}

	@Override
	public String echo(String msg) {
		return "impl.component " + msg;
	}

}
