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
package org.atomos.examples.substrate.felix;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.atomos.framework.AtomosRuntime;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

public class GogoConsole
{
    public static void main(String[] args) throws BundleException, ClassNotFoundException
    {
        long start = System.nanoTime();

        AtomosRuntime atomosRuntime = AtomosRuntime.newAtomosRuntime();
        Map<String, String> config = AtomosRuntime.getConfiguration(args);
        // Add this configs until we figure out why Felix cannot do module reflection for system packages
        config.put("org.osgi.framework.system.packages",
            "${osgi-exports},javax.management,javax.management.modelmbean,javax.management.remote,javax.naming,javax.net.ssl,javax.security.auth,javax.xml.parsers,org.xml.sax,org.xml.sax.helpers");
        config.put("felix.systempackages.substitution", "true");
        Framework framework = atomosRuntime.newFramework(config);
        framework.init();
        framework.start();

        long total = System.nanoTime() - start;
        System.out.println("Total time: " + TimeUnit.NANOSECONDS.toMillis(total));

        if (Arrays.asList(args).contains("-exit"))
        {
            System.exit(0);
        }
    }

}
