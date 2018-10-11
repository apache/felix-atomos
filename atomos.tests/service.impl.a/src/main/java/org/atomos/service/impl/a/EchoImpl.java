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
package org.atomos.service.impl.a;

import org.osgi.service.component.annotations.Component;
import org.atomos.service.contract.Echo;

@Component(property = {"type=impl.a.component"})
public class EchoImpl implements Echo {

	@Override
	public String echo(String msg) {
		return "impl.a.component " + msg;
	}

}
