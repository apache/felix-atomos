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
module service.image {
	requires atomos.framework;
	requires service.impl;
	requires service.impl.activator;
	requires org.apache.felix.scr;
	requires org.apache.felix.gogo.command;
	requires org.apache.felix.gogo.runtime;
	requires org.apache.felix.gogo.shell;
	requires jdk.jdwp.agent;
	uses AtomosRuntime;
}
