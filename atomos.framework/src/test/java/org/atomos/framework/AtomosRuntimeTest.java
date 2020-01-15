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
package org.atomos.framework;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class AtomosRuntimeTest {

	@Test
	public void testRuntimeConfigSimple() {
		String[] args = {"a=1", "b=2"};
		Map<String, String> map = AtomosRuntime.getConfiguration(args);
		assertEquals(2, map.size());
		assertEquals("1",map.get("a"));
		assertEquals("2",map.get("b"));
	}
	
	@Test
	public void testRuntimeConfig() {
		String[] args = {"calc=1+1=2", "separator=="};
		Map<String, String> map = AtomosRuntime.getConfiguration(args);
		assertEquals(2, map.size());
		assertEquals("1+1=2",map.get("calc"));
		assertEquals("=",map.get("separator"));
	}
	
	@Test
	public void testRuntimeConfigFilter() {
		String[] args = {"nono", "no:no"};
		Map<String, String> map = AtomosRuntime.getConfiguration(args);
		assertEquals(0, map.size());
	}

	
	@Test
	public void testRuntimeConfigSameKey() {
		String[] args = {"a=1", "a=2"};
		Map<String, String> map = AtomosRuntime.getConfiguration(args);
		assertEquals(1, map.size());
		assertEquals("2",map.get("a"));
	}
	
	@Test
	public void testRuntimeConfigNoValue() {
		String[] args = {"a="};
		Map<String, String> map = AtomosRuntime.getConfiguration(args);
		assertEquals(1, map.size());
		assertEquals("",map.get("a"));
	}
	@Test
	public void testRuntimeConfigEmpty() {
		String[] args = {};
		Map<String, String> map = AtomosRuntime.getConfiguration(args);
		assertTrue(map.isEmpty());
	}

	@Test
	public void testRuntimeConfigNull() {
		String[] args = null;
		Map<String, String> map = AtomosRuntime.getConfiguration(args);
		assertTrue(map.isEmpty());
	}
}
