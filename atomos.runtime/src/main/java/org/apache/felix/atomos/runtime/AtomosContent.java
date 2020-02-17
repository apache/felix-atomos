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
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

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
    public String getLocation();

    /**
     * The symbolic name of the Atomos content.
     * @return the symbolic name.
     */
    public String getSymbolicName();

    /**
     * The version of the Atomos content.
     * @return the version
     */
    public Version getVersion();

    /**
     * Adapt this Atomos content to the specified type. For example,
     * if running in a module layer then the module of the Atomos
     * content is returned in the optional value.
     * @param <A> The type to which this Atomos content is to be adapted.
     * @param type Class object for the type to which this Atomos content is to be
     *        adapted.
     * @return The object, of the specified type, to which this Atomos content has been
     *         adapted or {@code null} if this content cannot be adapted to the
     *         specified type.
     */
    public <T> Optional<T> adapt(Class<T> type);

    /**
     * The Atomos layer this Atomos content is in.
     * @return the Atomos layer
     */
    public AtomosLayer getAtomosLayer();

    /**
     * Installs this Atomos content as a connected bundle using the specified prefix.
     * If the Atomos content is already installed then the existing bundle is returned.
     * @param prefix
     * @return the installed connected bundle.
     * @throws BundleException if an error occurs installing the Atomos content
     */
    public Bundle install(String prefix) throws BundleException;
}
