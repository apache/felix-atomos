import org.atomos.framework.AtomosRuntime;

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
module service.test {
	requires atomos.framework;
	requires service.contract;
	requires service.impl;
	requires service.impl.activator;
	requires org.apache.felix.scr;
	requires osgi.promise;
	uses AtomosRuntime;
}
