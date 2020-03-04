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
package org.apache.felix.atomos.runtime;

import java.util.Optional;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.connect.ConnectContent;

/**
 * Atomos Content provides information about content discovered
 * by the Atomos runtime which can be installed as a connected
 * bundle into an OSGi Framework.
 */
public interface AtomosContent extends Comparable<AtomosContent>
{

    /**
     * The location of the Atomos content. The location
     * always includes the Atomos bundle's layer {@link AtomosLayer#getName() name}.
     * This location plus the {@link #install(String) prefix} can be used
     * as the install location of a connected bundle.
     * 
     * @return the location of the Atomos content.
     * @see AtomosContent#install(String)
     */
    String getAtomosLocation();

    /**
     * The symbolic name of the Atomos content.
     * @return the symbolic name.
     */
    String getSymbolicName();

    /**
     * The version of the Atomos content.
     * @return the version
     */
    Version getVersion();

    /**
     * Adapt this Atomos content to the specified type. For example,
     * if running in a module layer then the module of the Atomos
     * content is returned in the optional value.
     * @param <T> The type to which this Atomos content is to be adapted.
     * @param type Class object for the type to which this Atomos content is to be
     *        adapted.
     * @return The object, of the specified type, to which this Atomos content has been
     *         adapted or {@code null} if this content cannot be adapted to the
     *         specified type.
     */
    <T> Optional<T> adapt(Class<T> type);

    /**
     * The Atomos layer this Atomos content is in.
     * @return the Atomos layer
     */
    AtomosLayer getAtomosLayer();

    /**
     * Installs this Atomos content as a connected bundle using the specified location prefix.
     * If the Atomos content is already installed then the existing bundle is returned if
     * the existing bundle location is equal to the location this method calculates to
     * install the bundle; otherwise a {@code BundleException} is thrown.
     * This is a convenience method that is equivalent to the following:
     * <pre>
     * AtomosContent atomosContent = getAtomosContent();
     * BundleContext bc = getBundleContext();
     *
     * String osgiLocation = prefix + ":" + atomosContent.getAtomosLocation();
     * Bundle b = bc.getBundle(osgiLocation);
     * if (b != null)
     * {
     *   if (!b.getLocation().equals(osgiLocation))
     *   {
     *     throw new BundleException();
     *   }
     * }
     * atomosBundle.disconnect();
     * atomosBundle.connect(osgiLocation);
     * b = bc.installBundle(osgiLocation);
     * </pre>
     * @param prefix the prefix to use, if {@code null} then the prefix "atomos" will be used
     * @return the installed connected bundle.
     * @throws BundleException if an error occurs installing the Atomos content
     */
    Bundle install(String prefix) throws BundleException;

    /** 
     * Returns the connect content for this Atomos content. The returned {@link ConnectContent} can
     * be used to lookup entries from the content directly. If possible, it is preferred to used the bundle
     * returned by {@link #getBundle() getBundle()} instead to access entries from the content. Using the
     * bundle avoids issues with accessing the content
     * at the same time the OSGi Framework is managing the associated content.
     * <p>
     * If the ConnectContent is not managed by
     * a framework, {@link #getBundle() getBundle()} will return {@code null} and this method can be called as a way to access
     * the associated content. The caller is responsible for opening and closing the ConnectContent as appropriate.
     * 
     * @return ConnectContent associated with this Atomos content. 
     */
    ConnectContent getConnectContent();
    
    /**
     * Returns the connected bundle location for this Atomos content or {@code null} if 
     * no bundle location is connected for this content. A {@code non-null} value is
     * only an indication that this content {@code #connect(String)} has been called
     * to set the connect bundle location. A connected bundle may still need to be installed
     * into the framework using this bundle location.
     * @return the bundle location or {@code null}
     */
    String getConnectLocation();

    /**
     * Connects the specified bundle location to this Atomos content. Unlike
     * the {@link #install(String)} method, this method does not install this
     * content as a connected {@link Bundle}. If the specified location
     * is used with the {@link BundleContext#installBundle(String)} method then the
     * installed {@link Bundle} will be connected to this content. This method does
     * nothing if this content is already using the specified location as its
     * connect location.
     * @param bundleLocation the bundle location
     * @throws IllegalStateException if the connect location is already being used as a connect location
     * or if this content already has a different connect location set
     */
    void connect(String bundleLocation);

    /**
     * Disconnects this Atomos content from the bundle location, if the bundle location 
     * is set.  This method does nothing if this content is not connected.
     */
    void disconnect();

    /**
     * Returns the OSGi bundle installed which is connected with this Atomos content.
     * 
     * @return the OSGi bundle or {@code null} if there is no bundle connected
     *         with this content or if there is no OSGi Framework initialized
     *         with the Atomos Runtime.
     */
    Bundle getBundle();
}
