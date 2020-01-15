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
import org.atomos.framework.base.AtomosFrameworkUtilHelper;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.connect.FrameworkUtilHelper;

open module atomos.framework {
	exports org.atomos.framework;
	requires transitive org.eclipse.osgi;
	requires static osgi.annotation;
	requires static jdk.unsupported;
	requires static org.apache.felix.gogo.runtime;
	uses ConnectFrameworkFactory;
	provides FrameworkUtilHelper with AtomosFrameworkUtilHelper;
}
