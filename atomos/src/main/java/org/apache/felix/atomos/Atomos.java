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
package org.apache.felix.atomos;

import static org.apache.felix.atomos.impl.base.AtomosBase.NO_OP_HEADER_PROVIDER;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.BiFunction;

import org.apache.felix.atomos.AtomosLayer.LoaderType;
import org.apache.felix.atomos.impl.base.AtomosBase;
import org.osgi.annotation.versioning.ConsumerType;
import org.osgi.annotation.versioning.ProviderType;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.connect.ConnectContent;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.connect.ModuleConnector;
import org.osgi.framework.launch.Framework;

/**
 * Atomos can be used for creating new OSGi {@link Framework} instances 
 * with bundles loaded from the module or class
 * path. The Framework instance will have a set of contents discovered and
 * installed as connected bundles automatically when the Framework is
 * {@link Framework#init() initialized}.
 * <p>
 * When loading Atomos from the module path the contents will be loaded from the
 * Java modules included on the module path and will use the class loader
 * provided by the Java module system. This implies that the normal protection
 * provided by the OSGi module layer will not be available to the classes loaded
 * out of the module path bundles. Classes in one bundle will be able to load
 * classes from other bundles even when the package is not exported. But the
 * Java module system will provide protection against modules trying to execute
 * code from packages that are not exported. This means you may find cases where
 * a bundle can load a class from another bundle but then get runtime exceptions
 * when trying to execute methods on the class. Other limitations are also
 * imposed because a single class loader is used to load all contents from connected
 * bundles. For example, multiple versions of a package cannot be supported because the class
 * loader can only define a single package of a given name.
 * <p>
 * When loading from the module path Atomos contents are discovered using the
 * module layer which loads the Atomos framework. This is typically the boot
 * layer. Atomos contents are discovered by searching the current module layer
 * for the available modules as well as the modules available in the parent
 * layers until the {@link ModuleLayer#empty() empty} layer is reached.
 * <p>
 * When loading Atomos from the class path (i.e as an unnamed module) the
 * contents will be loaded from JARs included on the class path along side the
 * Atomos JARs and will use the application class loader provided by the JVM.
 * This also implies that the normal protection provided by the OSGi module
 * layer will not be available to the classes loaded out of the class path
 * bundles. But unlike when loading bundles from the module path, the Java
 * module system will not provide any protection against executing code from
 * other bundles from private packages. This mode also suffers from the typical
 * issues that arise if you have multiple JARs on the class path that provide
 * the same package. When in module path mode the JVM will fail to launch and
 * inform you that it detected duplicate packages from the modules on the module
 * path. In class path mode the JVM does no such check, this leaves you open
 * to have unexpected shadowing if you have multiple versions of the same package
 * on the class path.
 * <p>
 * When loading from the class path Atomos contents are discovered by searching
 * the class path for bundle manifests (META-INF/MANIFEST.MF) resources and
 * discovering any that contain OSGI meta-data (e.g. Bundle-SymbolicName). The
 * boot layer is also searched and loads all boot modules as bundles similar to
 * when loading from the module path.
 * <p>
 * The Framework can be created using the {@link ConnectFrameworkFactory} APIs.
 * The following will launch using the standard {@link ConnectFrameworkFactory}:
 * 
 * <pre>
 * {@code Map<String, String>} config = getConfiguration();
 * config.put(Constants.FRAMEWORK_SYSTEMPACKAGES, "");
 * ModuleConnector atomosConnector = Atomos.newAtomos().newModuleConnector();
 * Framework framework = ServiceLoader.load(ConnectFrameworkFactory.class).iterator().next().newFramework(config, atomosConnector);
 * framework.start();
 * </pre>
 * 
 * Note that the {@link Constants#FRAMEWORK_SYSTEMPACKAGES} must be set to an
 * empty value to prevent discovery of packages from the
 * {@link ModuleLayer#boot() boot} layer. Without doing this the OSGi R7
 * framework will discover all modules from the boot layer and assume they all
 * should be exported by the system bundle. That is normally desired so the
 * packages available from the boot layer (typically the modules provided by the
 * Java platform itself) can be available for import from bundles installed into
 * the framework. But with Atomos you may load all your bundles within the boot
 * layer itself. In that case you obviously do not want all the packages from
 * the bundles in the boot layer also be exported by the system bundle. Setting
 * {@link Constants#FRAMEWORK_SYSTEMPACKAGES} to the empty value will prevent
 * this from happening. Atomos still needs to make the packages exported by the
 * Java platform modules available for import. In order to do this Atomos will
 * create an {@link AtomosContent Atomos content} for each module contained in
 * the {@link ModuleLayer#boot() boot} layer.
 * <p>
 * The following code uses the Atomos to first create a child layer which
 * is then used to load more modules in addition to the ones included on the
 * module path.
 * 
 * <pre>
 * Atomos atomos = Atomos.newAtomos();
 * 
 * // Add a new layer using a Path that contains a set of modules to load
 * Path modulesDir = getModulesDir();
 * atomos.getBootLayer().addLayer("child", LoaderType.OSGI, modulesDir);
 *
 * Framework framework = atomos.newFramework(frameworkConfig);
 * framework.start();
 * </pre>
 * 
 * When using the {@link Atomos#newFramework(Map)} to create a 
 * new Framework the {@link Constants#FRAMEWORK_SYSTEMPACKAGES}
 * is set to the empty value automatically.
 * 
 * By default all Atomos contents will be installed and started in the OSGi
 * framework when the framework is started. If {@link #ATOMOS_CONTENT_INSTALL
 * atomos.content.install} is set to <code>false</code> in the framework
 * configuration then the boot contents will not be installed by default. In that
 * case the {@link AtomosContent#install(String)} method can be used to
 * selectively install Atomos contents. If {@link #ATOMOS_CONTENT_START
 * atomos.content.start} is set to <code>false</code> in the framework
 * configuration then the Atomos bundles will not be started by default. The
 * system.bundle of the initialized framework will also have an Atomos
 * service registered with its bundle context.
 */
@org.osgi.annotation.bundle.Header(name = "Main-Class", value = "org.apache.felix.atomos.Atomos")
@ProviderType
public interface Atomos
{
    /**
     * Framework launching property specifying if the Atomos contents
     * will not be automatically installed as bundles. Default is true, which
     * will install all discovered Atomos content as bundles.
     */
    String ATOMOS_CONTENT_INSTALL = AtomosBase.ATOMOS_PROP_PREFIX
        + "content.install";
    /**
     * Framework launching property specifying if the Atomos contents installed
     * as connected bundles will not be marked for start. Default is true, which
     * will start all discovered Atomos content that are installed as bundles.
     */
    String ATOMOS_CONTENT_START = AtomosBase.ATOMOS_PROP_PREFIX + "content.start";

    /**
     * Returns the Atomos content that is connected with the specified bundle location.
     * The Atomos content returned is used by the connected bundle installed
     * in the framework with the specified bundle location.
     * 
     * @param bundleLocation the bundle location.
     * @return the Atomos content with the specified location or {@code null} if no
     *         Atomos content is installed at the location.
     */
    AtomosContent getConnectedContent(String bundleLocation);

    /**
     * The initial Atomos boot layer. Depending on the mode Atomos is running
     * this may be the backed by {@link ModuleLayer#boot()} or by the
     * class path.
     * 
     * @return the boot Atomos layer
     */
    AtomosLayer getBootLayer();

    /**
     * Returns the module connector for this runtime instance.
     * The module connector can be used to create a new framework
     * by using a {@link ConnectFrameworkFactory} directly by calling
     * the {@link ConnectFrameworkFactory#newFramework(Map, ModuleConnector)}
     * method.
     * @return the module connector for this runtime
     */
    ModuleConnector getModuleConnector();

    /**
     * Adds a layer as a child of the specified parents and loads modules from the specified
     * module paths
     * 
     * @param parents     the parent layers. Must include at least one parent layer
     * @param name        the name of the new layer
     * @param loaderType  the type of class loader to use
     * @param modulePaths the paths to load modules for the new layer
     * @return a newly created layer
     * @throws UnsupportedOperationException if the {@link #getBootLayer() boot} layer
     *  {@link AtomosLayer#isAddLayerSupported()} returns false.
     */
    AtomosLayer addLayer(List<AtomosLayer> parents, String name, LoaderType loaderType,
        Path... modulePaths);

    /**
     * Creates a new {@link Framework} instance that uses this Atomos instance. The
     * {@link ServiceLoader} is used to load an implementation of a {@link ConnectFrameworkFactory}
     * which is used to create a new {@link Framework} instance with the specified Atomos runtime.
     * The supplied framework configuration is used to create the new {@code Framework} instance.
     * Additional configuration options maybe configured automatically in order to correctly configure
     * the system packages for the {@code Framework} instance.
     * @param frameworkConfig The framework configuration options, or {@code null} if the defaults should be used
     * @return The new uninitialized Framework instance which uses this Atomos instance
     */
    Framework newFramework(Map<String, String> frameworkConfig);

    /**
     * A main method that can be used by executable jars to initialize and start
     * Atomos with an available OSGi {@link Framework} implementation.
     * Each string in the arguments array may contain a key=value
     * pair that will be used for the framework configuration.
     * 
     * @param args the args will be converted into a {@code Map<String, String>} to
     *             use as configuration parameters for the OSGi Framework.
     * @throws BundleException when an error occurs
     * @see #newAtomos(Map)
     * @see #newFramework(Map)
     */
    static void main(String... args) throws BundleException
    {
        Map<String, String> config = getConfiguration(args);
        // default to reporting resolution issues from main
        config.putIfAbsent(AtomosBase.ATOMOS_REPORT_RESOLUTION_PROP, "true");
        Atomos atomos = Atomos.newAtomos(config);
        Framework framework = atomos.newFramework(config);
        framework.start();
    }

    /**
     * Converts a string array into a {@code  Map<String,String>}
     * 
     * @param args the arguments where each element has key=string value, the key
     *             cannot contain an '=' (equals) character.
     * @return a map of the configuration specified by the args
     */
    static Map<String, String> getConfiguration(String... args)
    {
        Map<String, String> config = new HashMap<>();
        if (args != null)
        {
            for (String arg : args)
            {
                int equals = arg.indexOf('=');
                if (equals != -1)
                {
                    String key = arg.substring(0, equals);
                    String value = arg.substring(equals + 1);
                    config.put(key, value);
                }
            }
        }
        return config;
    }

    /**
     * Creates a new Atomos that can be used to create a new OSGi framework
     * instance. Same as calling {@code newAtomos(Map)} with an empty 
     * configuration.
     * 
     * @return a new Atomos.
     */
    static Atomos newAtomos()
    {
        return newAtomos(NO_OP_HEADER_PROVIDER);
    }

    /**
     * Creates a new Atomos that can be used to create a new OSGi framework
     * instance. Same as calling {@code newAtomos(BiFunction,Map)} with an empty
     * configuration.
     *
     * @param headerProvider the header provider function
     * @return a new Atomos.
     */
    static Atomos newAtomos(HeaderProvider headerProvider)
    {
        return newAtomos(Collections.emptyMap(), headerProvider);
    }

    /**
     * Creates a new Atomos that can be used to create a new OSGi framework
     * instance. Same as calling {@code newAtomos(BiFunction,Map)} with a
     * no-op {@code headerProvider} function.
     *
     * @param configuration the properties to configure the new Atomos
     * @return a new Atomos.
     */
    static Atomos newAtomos(Map<String, String> configuration)
    {
        return newAtomos(configuration, NO_OP_HEADER_PROVIDER);
    }

    /**
     * Creates a new Atomos that can be used to create a new OSGi framework
     * instance. If Atomos is running as a Java Module then this Atomos can
     * be used to create additional layers by using the
     * {@link AtomosLayer#addLayer(String, AtomosLayer.LoaderType, Path...)} method. If the additional layers are added
     * before {@link ConnectFrameworkFactory#newFramework(Map, ModuleConnector)}  creating} and {@link Framework#init()
     * initializing} the framework then the Atomos contents found in the added layers
     * will be automatically installed and started according to the
     * {@link #ATOMOS_CONTENT_INSTALL} and {@link #ATOMOS_CONTENT_START} options.
     * <p>
     * Note that this {@code Atomos} must be used for creating a new framework instance
     * with the method {@link ConnectFrameworkFactory#newFramework(Map, ModuleConnector)} to use
     * the layers added to this {@code Atomos} or the {@link #newFramework(Map)} method can
     * be called on this {@code Atomos}.
     * <p>
     * The given headerProvider function maps each Atomos content
     * {@link AtomosContent#getAtomosLocation() location}
     * and existing headers of the content to a new optional map of headers.
     * The resulting map will be used as the headers for the {@link ConnectContent#getHeaders()}.
     * If the function returns an empty optional then the existing
     * headers will be used.
     *
     * @param configuration the properties to configure the new Atomos
     * @param headerProvider a function that will be called with the location and the existing headers for each Atomos content.
     * @return a new Atomos.
     */
    static Atomos newAtomos(Map<String, String> configuration,
        HeaderProvider headerProvider)
    {
        return AtomosBase.newAtomos(configuration, headerProvider);
    }

    /**
     * A function that maps each {@code AtomosContent} {@link AtomosContent#getAtomosLocation() location}
     * and its existing headers to a new optional map of headers to be used for the 
     * {@link ConnectContent#getHeaders() headers} of the {@link AtomosContent#getConnectContent() ConnectContent}. 
     */
    @FunctionalInterface
    @ConsumerType
    interface HeaderProvider extends BiFunction<String, Map<String, String>, Optional<Map<String, String>>>
    {

        /**
         * Applies this header provider function to the specified 
         * {@code AtomosContent} {@link AtomosContent#getAtomosLocation() location} and
         * map of existing headers.  The returned {@code Optional} map of headers will
         * be used by the {@link ConnectContent#getHeaders()} method for the
         * {@code ConnectContent} {@link AtomosContent#getConnectContent() associated}
         * with the {@code AtomosContent} that has the specified 
         * {@link AtomosContent#getAtomosLocation() location}.
         * <p>
         * This method allows a header provider to augment existing bundle manifest
         * headers or add completely new bundle manifest headers that are not present
         * in the existing headers.
         * <p>
         * This function may be applied before the instance of the {@code AtomosContent}
         * instance is created which may result in the symbolic name and or version
         * of the {@code AtomosContent} to be influenced by this function.
         * @param location The {@code AtomosContent} {@link AtomosContent#getAtomosLocation() location}
         * @param existingHeaders The existing headers found for the {@code AtomosContent}
         * @return the {@code Optional} map of headers to use instead of the {@code existingHeaders}. If 
         * the existing headers should be used then an empty {@code Optional} may be returned.
         */
        @Override
        Optional<Map<String, String>> apply(String location,
            Map<String, String> existingHeaders);
    }
}
