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
import org.atomos.framework.AtomosRuntime;
import org.atomos.framework.impl.AtomosFrameworkUtilHelper;
import org.atomos.framework.impl.AtomosRuntimeImpl;
import org.eclipse.osgi.internal.hookregistry.FrameworkUtilHelper;

open module atomos.framework {
	exports org.atomos.framework;
	requires transitive org.eclipse.osgi;
	requires static osgi.annotation;
	uses org.osgi.framework.launch.FrameworkFactory;
	provides AtomosRuntime with AtomosRuntimeImpl;
	provides FrameworkUtilHelper with AtomosFrameworkUtilHelper;
	uses AtomosRuntime;
}
