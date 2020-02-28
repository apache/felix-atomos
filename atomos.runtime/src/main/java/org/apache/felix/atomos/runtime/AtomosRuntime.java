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
import java.util.Collections;
import java.util.Map;

import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase;
import org.apache.felix.atomos.launch.AtomosLauncher;
import org.osgi.framework.Constants;
import org.osgi.framework.connect.ConnectFrameworkFactory;
import org.osgi.framework.connect.ModuleConnector;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * The Atomos runtime can be used for creating new OSGi
 * {@link Framework} instances with bundles loaded from the module or class
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
 * The Framework can be created using the {@link FrameworkFactory} APIs like a
 * normal OSGi Framework or for more advanced scenarios the Atomos runtime can
 * be used to setup additional module layer configurations before launching the
 * framework.
 * <p>
 * The following will launch using the standard {@link ConnectFrameworkFactory}:
 * 
 * <pre>
 * {@code Map<String, String>} config = getConfiguration();
 * config.put(Constants.FRAMEWORK_SYSTEMPACKAGES, "");
 * ModuleConnector atomosConnect = AtomosRuntime.newAtomosRuntime().newModuleConnector();
 * Framework framework = ServiceLoader.load(ConnectFrameworkFactory.class).iterator().next().newFramework(config, atomosConnect);
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
 * The following code uses the AtomosRuntime to first create a child layer which
 * is then used to load more modules in addition to the ones included on the
 * module path.
 * 
 * <pre>
 * AtomosRuntime atomosRuntime = AtomosRuntime.newAtomosRuntime();
 * 
 * // Add a new layer using a Path that contains a set of modules to load
 * Path modulesDir = getModulesDir();
 * atomosRuntime.getBootLayer.addLayer("child", LoaderType.OSGI, modulesDir);
 *
 * // The Atomos runtime must be used to create the framework in order
 * // use the additional children layers added
 * Framework framework = AtomosLauncher.newFramework(frameworkConfig, atomosRuntime);
 * framework.start();
 * </pre>
 * 
 * When using the {@link AtomosLauncher#newFramework(Map, AtomosRuntime)} to create a 
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
 * system.bundle of the initialized framework will also have an AtomosRuntime
 * service registered with its bundle context.
 */
public interface AtomosRuntime
{
    /**
     * Framework launching property specifying if the Atomos contents
     * will not be automatically installed as bundles. Default is true, which
     * will install all discovered Atomos content as bundles.
     */
    String ATOMOS_CONTENT_INSTALL = AtomosRuntimeBase.ATOMOS_PROP_PREFIX
        + "content.install";
    /**
     * Framework launching property specifying if the Atomos contents installed
     * as connected bundles will not be marked for start. Default is true, which
     * will start all discovered Atomos content that are installed as bundles.
     */
    String ATOMOS_CONTENT_START = AtomosRuntimeBase.ATOMOS_PROP_PREFIX + "content.start";

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
     * Creates a new AtomosRuntime that can be used to create a new OSGi framework
     * instance. Same as calling {@code newAtomosRuntime(Map)} with a {@code null} 
     * configuration.
     * 
     * @return a new AtomosRuntime.
     */
    static AtomosRuntime newAtomosRuntime()
    {
        return newAtomosRuntime(Collections.emptyMap());
    }

    /**
     * Creates a new AtomosRuntime that can be used to create a new OSGi framework
     * instance. If Atomos is running as a Java Module then this AtomosRuntime can
     * be used to create additional layers by using the
     * {@link AtomosLayer#addLayer(String, AtomosLayer.LoaderType, Path...)} method. If the additional layers are added
     * before {@link ConnectFrameworkFactory#newFramework(Map, ModuleConnector)}  creating} and {@link Framework#init()
     * initializing} the framework then the Atomos contents found in the added layers
     * will be automatically installed and started according to the
     * {@link #ATOMOS_CONTENT_INSTALL} and {@link #ATOMOS_CONTENT_START} options.
     * <p>
     * Note that this {@code AtomosRuntime} must be used for creating a new
     * {@link ConnectFrameworkFactory#newFramework(Map, ModuleConnector)} instance to use
     * the layers added to this {@code AtomosRuntime}.
     * 
     * @param configuration the properties to configure the new runtime
     * @return a new AtomosRuntime.
     */
    static AtomosRuntime newAtomosRuntime(Map<String, String> configuration)
    {
        return AtomosRuntimeBase.newAtomosRuntime(configuration);
    }
}
