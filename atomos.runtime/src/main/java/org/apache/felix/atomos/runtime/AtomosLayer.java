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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.felix.atomos.runtime.AtomosRuntime.LoaderType;
import org.osgi.framework.BundleException;

/**
 * An Atomos Layer may represents a {@link ModuleLayer} that was added to
 * a {@link AtomosRuntime} using the {@link AtomosRuntime#addLayer(Configuration) addLayer}
 * method or the Atomos Layer could represent the {@link AtomosRuntime#getBootLayer() boot layer}.
 * An Atomos Layer will contain one or more {@link AtomosBundleInfo atomos bundles} which can
 * then be used to {@link AtomosBundleInfo#install(String) install } them as OSGi bundles into the
 * {@link AtomosRuntime#newFramework(java.util.Map) framework}.
 */
public interface AtomosLayer
{
    /**
     * Adapt this Atomos layer to the specified type. For example,
     * if running in a module layer then the layer can be adapted
     * to a ModuleLayer associated with this Atomos Layer.
     * @param <A> The type to which this Atomos bundle is to be adapted.
     * @param type Class object for the type to which this Atomos bundle is to be
     *        adapted.
     * @return The object, of the specified type, to which this Atomos bundle has been
     *         adapted or {@code null} if this bundle cannot be adapted to the
     *         specified type.
     */
    public <T> Optional<T> adapt(Class<T> type);

    /**
     * The Atomos Layer children of this layer
     * @return The children of this layer
     */
    Set<AtomosLayer> getChildren();

    /**
     * The Atomos parents of this layer
     * @return the parnets of this layer
     */
    List<AtomosLayer> getParents();

    /**
     * The Atomos bundles contained in this layer
     * @return the Atomos Bundles
     */
    Set<AtomosBundleInfo> getAtomosBundles();

    /**
     * Returns the Atomos bundle with the given name in this layer, or if not in this
     * layer, the {@linkplain #getParents() parent} layers. Finding a bundle in
     * parent layers is equivalent to invoking {@code findAtomosBundle} on each
     * parent, in search order, until the bundle is found or all parents have
     * been searched. In a <em>tree of layers</em>  then this is equivalent to
     * a depth-first search.
     * @param symbolicName the name of the bundle to find
     * @return The bundle with the given name or an empty {@code Optional}
     *         if there isn't a bundle with this name in this layer or any
     *         parent layer
     */
    Optional<AtomosBundleInfo> findAtomosBundle(String symbolicName);

    /**
     * The name of the Atomos Layer.  By default the Atomos Layer
     * name is the empty string.  Atomos Layer names are not
     * required to be unique.  All Atomos bundles contained in a
     * layer will have {@link AtomosBundleInfo#getLocation() locations}
     * that use the layer name as a prefix.  If the layer
     * name is not the empty string then the location prefix will be
     * the layer name followed by a colon ({@code :}).
     * This allows two different layers to load the same module in
     * different layers.
     * @return the name of the layer
     */
    String getName();

    /**
     * Returns this Atomos Layer's unique identifier. This Atomos Layer is assigned a unique
     * identifier when it was installed in the Atomos runtime.
     * 
     * <p>
     * A Atomos Layer's unique identifier has the following attributes:
     * <ul>
     * <li>Is unique and persistent.</li>
     * <li>Is a {@code long}.</li>
     * <li>Its value is not reused for another layer, even after a layer is
     * uninstalled.</li>
     * <li>Does not change while a layer remains installed.</li>
     * </ul>
     * 
     * @return The unique identifier of this layer.
     */
    long getId();

    /**
     * Returns the loader type used for this Atomos layer.
     * @return the loader type
     */
    LoaderType getLoaderType();

    /**
     * Uninstalls this Atomos Layer along with any {@link #getChildren() children}
     * layers.
     * @throws BundleException 
     */
    void uninstall() throws BundleException;
}
