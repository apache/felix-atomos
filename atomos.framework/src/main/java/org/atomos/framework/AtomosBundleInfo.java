/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atomos.framework;

import java.util.Optional;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;

/**
 * Information about an Atomos bundle.
 */
public interface AtomosBundleInfo extends Comparable<AtomosBundleInfo> {

	/**
	 * The location of the Atomos bundle. The location
	 * always includes the Atomos bundle's layer {@link AtomosLayer#getName() name}.
	 * This location plus the {@link #install(String) prefix} will be used
	 * as the install location of the Atomos bundle.
	 * 
	 * @return the location of the Atomos bundle.
	 * @see AtomosBundleInfo#install(String)
	 */
	public String getLocation();

	/**
	 * The symbolic name of the Atomos bundle.
	 * @return the symbolic name.
	 */
	public String getSymbolicName();

	/**
	 * The version of the Atomos bundle.
	 * @return the version
	 */
	public Version getVersion();

	/**
	 * Adapt this Atomos bundle to the specified type. For example,
	 * if running in a module layer then the module of the Atomos
	 * bundle is returned in the optional value.
	 * @param <A> The type to which this Atomos bundle is to be adapted.
	 * @param type Class object for the type to which this Atomos bundle is to be
	 *        adapted.
	 * @return The object, of the specified type, to which this Atomos bundle has been
	 *         adapted or {@code null} if this bundle cannot be adapted to the
	 *         specified type.
	 */
	public <T> Optional<T> adapt(Class<T> type);

	/**
	 * The Atomos layer this Atomos bundle is in.
	 * @return the Atomos layer
	 */
	public AtomosLayer getAtomosLayer();

	/**
	 * Installs this Atomos bundle using the specified prefix.  If the Atomos bundle
	 * is already installed then the existing bundle is returned.
	 * @param prefix
	 * @return the installed Atomos bundle.
	 * @throws BundleException if an error occurs installing the Atomos bundle
	 */
	public Bundle install(String prefix) throws BundleException;

	public ConnectContent getConnectContent();

}
