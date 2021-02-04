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
package org.apache.felix.atomos.launch;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.felix.atomos.impl.runtime.base.AtomosRuntimeBase;
import org.apache.felix.atomos.runtime.AtomosLayer;
import org.apache.felix.atomos.runtime.AtomosRuntime;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

/**
 * The Atomos launcher contains convenience methods for creating and launching
 * an OSGi {@link Framework} instance with the Atomos runtime.
 */
@org.osgi.annotation.bundle.Header(name = "Main-Class", value = org.apache.felix.atomos.launch.AtomosLauncher.ATOMOS_LAUNCHER)
public class AtomosLauncher
{
    // package private constant use to specify the main class
    static final String ATOMOS_LAUNCHER = "org.apache.felix.atomos.launch.AtomosLauncher";
    /**
     * A main method that can be used by executable jars to initialize and start an
     * Atomos Runtime with an available OSGi {@link Framework} implementation.
     * Each string in the arguments array may contain a key=value
     * pair that will be used for the framework configuration.
     * 
     * @param args the args will be converted into a {@code Map<String, String>} to
     *             use as configuration parameters for the OSGi Framework.
     * @throws BundleException when an error occurs
     * @see AtomosLauncher#launch(Map)
     */
    public static void main(String[] args) throws BundleException
    {
        launch(getConfiguration(args));
    }

    /**
     * A configuration option used by {@link #launch(Map)} which can be used to
     * configuration a modules folder to load additional Atomos contents from.
     */
    public static final String ATOMOS_MODULES_DIR = "atomos.modules";

    /**
     * Convenience method that creates an AtomosRuntime in order to load the Atomos
     * contents discovered in the environment. If additional layers are supported by the
     * environment (see {@link AtomosLayer#isAddLayerSupported()} then additional modules
     * may be loaded into a child layer by looking for a
     * modules folder. The path to the modules folder can be configured by using the
     * {@link #ATOMOS_MODULES_DIR atomos.modules} launch option. 
     * If the {@link #ATOMOS_MODULES_DIR atomos.modules}
     * option is not specified in the frameworkConfig then the default will try to
     * determine the location on disk of the Atomos runtime module and look for a
     * folder called "modules". If the location of the Atomos Runtime module
     * cannot be determined then no additional modules folder will be searched.
     * 
     * @param frameworkConfig the framework configuration
     * @return a new framework instance which has been started with the Atomos runtime.
     * @throws BundleException if an error occurred creating and starting the framework
     * @see AtomosLauncher#newFramework(Map, AtomosRuntime)
     */
    public static Framework launch(Map<String, String> frameworkConfig)
        throws BundleException
    {
        frameworkConfig = new HashMap<>(frameworkConfig);
        // default to reporting resolution issues from launcher
        frameworkConfig.putIfAbsent(AtomosRuntimeBase.ATOMOS_REPORT_RESOLUTION_PROP,
            "true");
        AtomosRuntime atomosRuntime = AtomosRuntime.newAtomosRuntime(frameworkConfig);
        if (atomosRuntime.getBootLayer().isAddLayerSupported())
        {
            String modulesDirPath = frameworkConfig.get(ATOMOS_MODULES_DIR);
            Path modulesPath = modulesDirPath == null ? null
                : new File(modulesDirPath).toPath();
            atomosRuntime.getBootLayer().addModules("modules", modulesPath);
        }

        Framework framework = atomosRuntime.newFramework(frameworkConfig);
        framework.start();
        return framework;
    }

    /**
     * Converts a string array into a {@code  Map<String,String>}
     * 
     * @param args the arguments where each element has key=string value, the key
     *             cannot contain an '=' (equals) character.
     * @return a map of the configuration specified by the args
     */
    public static Map<String, String> getConfiguration(String[] args)
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
}
