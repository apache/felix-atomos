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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.felix.atomos.runtime.AtomosRuntime.LoaderType;
import org.osgi.framework.BundleException;

/**
 * An Atomos Layer may represents a {@link ModuleLayer} that was added to
 * a {@link AtomosRuntime} using the {@link AtomosRuntime#addLayer(Configuration) addLayer}
 * method or the Atomos Layer could represent the {@link AtomosRuntime#getBootLayer() boot layer}.
 * An Atomos Layer will contain one or more {@link AtomosContent Atomos contents} which can
 * then be used to {@link AtomosContent#install(String) install } them as OSGi connected bundles into the
 * {@link AtomosRuntime#newFramework(java.util.Map) framework}.
 */
public interface AtomosLayer
{
    /**
     * Adapt this Atomos layer to the specified type. For example,
     * if running in a module layer then the layer can be adapted
     * to a ModuleLayer associated with this Atomos Layer.
     * @param <A> The type to which this Atomos layer is to be adapted.
     * @param type Class object for the type to which this Atomos layer is to be
     *        adapted.
     * @return The object, of the specified type, to which this Atomos layer has been
     *         adapted or {@code null} if this layer cannot be adapted to the
     *         specified type.
     */
    public <T> Optional<T> adapt(Class<T> type);

    /**
     * Adds a layer as a child of this layer and loads modules from the specified
     * module paths
     * 
     * @param name        the name of the new layer
     * @param loaderType  the type of class loader to use
     * @param modulePaths the paths to load modules for the new layer
     * @return a newly created layer
     * @throws UnsupportedOperationException if {@link #isAddLayerSupported()} returns false.
     */
    AtomosLayer addLayer(String name, LoaderType loaderType, Path... modulePaths);

    /**
     * A convenience method that adds the modules found at the specified path
     * to a new child layer of this layer.
     * @param name The name of the layer.
     * @param path The path to the modules.  If {@code null} then the default will try to
     * determine the location on disk of the atomos runtime module and look for a
     * folder with the same name as the specified name of the layer.
     * @throws UnsupportedOperationException if {@link #isAddLayerSupported()} returns false.
     */
    public AtomosLayer addModules(String name, Path path);

    /**
     * Returns {@code true} if additional layers are supported.
     * @return if modules and additional layers are supported.
     */
    boolean isAddLayerSupported();

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
     * The Atomos contents contained in this layer
     * @return the Atomos contents
     */
    Set<AtomosContent> getAtomosContents();

    /**
     * Returns the Atomos content with the given name in this layer, or if not in this
     * layer, the {@linkplain #getParents() parent} layers. Finding content in
     * parent layers is equivalent to invoking {@code findAtomosContent} on each
     * parent, in search order, until the content is found or all parents have
     * been searched. In a <em>tree of layers</em>  then this is equivalent to
     * a depth-first search.
     * @param symbolicName the name of the content to find
     * @return The content with the given name or an empty {@code Optional}
     *         if there isn't a content with this name in this layer or any
     *         parent layer
     */
    Optional<AtomosContent> findAtomosContent(String symbolicName);

    /**
     * The name of the Atomos Layer.  By default the Atomos Layer
     * name is the empty string.  Atomos Layer names are not
     * required to be unique.  All Atomos contents contained in a
     * layer will have {@link AtomosBundleInfo#getAtomosLocation() locations}
     * that use the layer name as a prefix.  If the layer
     * name is not the empty string then the location prefix will be
     * the layer name followed by a colon ({@code :}).
     * This allows two different layers to load the same content in
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
