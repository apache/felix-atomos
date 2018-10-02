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
package org.atomos.service.test;

import java.util.Map;

import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.atomos.framework.AtomosRuntime;

public class Launch 
{
	private static volatile Framework framework;
    public static void main( String[] args ) throws BundleException
    {
       	framework = AtomosRuntime.createAtomRuntime().createFramework(Map.of());
       	framework.start();
    }
    public static Framework getFramework() {
    	return framework;
    }
}
