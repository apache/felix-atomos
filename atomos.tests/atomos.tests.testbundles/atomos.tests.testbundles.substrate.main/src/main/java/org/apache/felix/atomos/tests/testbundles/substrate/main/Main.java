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
package org.apache.felix.atomos.tests.testbundles.substrate.main;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.felix.atomos.launch.AtomosLauncher;
import org.apache.felix.atomos.runtime.AtomosRuntime;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerContext;

public class Main
{
    public static void main(String[] args) throws BundleException, ClassNotFoundException
    {
        final long start = System.nanoTime();

        final AtomosRuntime atomosRuntime = AtomosRuntime.newAtomosRuntime();
        final Map<String, String> config = AtomosLauncher.getConfiguration(args);
        config.putIfAbsent(LoggerContext.LOGGER_CONTEXT_DEFAULT_LOGLEVEL,
            LogLevel.AUDIT.name());
        final Framework framework = AtomosLauncher.newFramework(config, atomosRuntime);
        framework.init();
        final BundleContext bc = framework.getBundleContext();
        final LogReaderService logReader = bc.getService(
            bc.getServiceReference(LogReaderService.class));
        logReader.addLogListener((e) -> {
            System.out.println(getLogMessage(e));
        });
        framework.start();

        final long total = System.nanoTime() - start;
        System.out.println("Total time: " + TimeUnit.NANOSECONDS.toMillis(total));

        if (Arrays.asList(args).contains("-exit"))
        {
            System.exit(0);
        }
    }

    private static String getLogMessage(LogEntry e)
    {
        final StringBuilder builder = new StringBuilder(e.getMessage());
        if (e.getBundle() != null)
        {
            builder.append(" - bundle: " + e.getBundle());
        }
        if (e.getServiceReference() != null)
        {
            builder.append(" - service: " + e.getServiceReference());
        }
        return builder.toString();
    }
}
