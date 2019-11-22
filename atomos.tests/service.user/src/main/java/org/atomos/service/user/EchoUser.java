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
package org.atomos.service.user;

import org.atomos.service.contract.Echo;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = EchoUser.class, property = {"echo.reference:Boolean=true"}, immediate = true)
@org.osgi.annotation.bundle.Requirement(namespace = "osgi.ee", filter = "(&(osgi.ee=JavaSE)(version=1.8))")
public class EchoUser {
	@Activate
	public void activate() {
		System.out.println("Activated: " + getClass().getName());
	}

	@Reference
	protected void setEcho(Echo echo) {
		System.out.println("Echo service found: " + echo.echo("hello"));
	}

	protected void unsetEcho(Echo echo) {
		System.out.println("Echo service unset: " + echo.echo("goodbye"));
	}
}
