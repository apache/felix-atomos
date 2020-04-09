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
package org.apache.felix.atomos.utils.core.plugins;

import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.felix.atomos.utils.api.Config;
import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.api.plugin.JarPlugin;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultResourceConfiguration;

public class ResourcePlugin implements JarPlugin<Config>
{
    private static String CLASS_SUFFIX = ".class";
    private static Collection<String> EXCLUDE_DIRS = Collections.unmodifiableList(
        Arrays.asList("META-INF/", "OSGI-INF/", "OSGI-OPT/"));

    private static Collection<String> EXCLUDE_NAMES = Collections.unmodifiableList(
        Arrays.asList("/packageinfo"));
    private static String FRENCH_BUNDLE_CLASS = "_fr.class";

    private static String FRENCH_BUNDLE_PROPS = "_fr.properties";
    private static String SERVICES = "META-INF/services/";

    @Override
    public void doJar(JarFile jar, Context context, URLClassLoader classLoader)
    {

        DefaultResourceConfiguration resourceConfig = new DefaultResourceConfiguration();
        // TODO Auto-generated method stub
        pattern: for (JarEntry entry : jar.stream().collect(Collectors.toList()))
        {
            String entryName = entry.getName();
            if (entry.isDirectory())
            {
                continue;
            }
            if (entryName.indexOf('/') == -1)
            {
                continue;
            }
            if (entryName.startsWith(SERVICES))
            {
                resourceConfig.addResourcePattern(entryName);
                continue;
            }
            for (String excluded : EXCLUDE_NAMES)
            {
                if (entryName.endsWith(excluded))
                {
                    continue pattern;
                }
            }
            for (String excluded : EXCLUDE_DIRS)
            {
                if (entryName.startsWith(excluded))
                {
                    continue pattern;
                }
            }
            if (entryName.endsWith(CLASS_SUFFIX))
            {
                // just looking for resource bundle for french as an indicator
                if (entryName.endsWith(FRENCH_BUNDLE_CLASS))
                {
                    String bundleName = entryName.substring(0,
                        entryName.length() - FRENCH_BUNDLE_CLASS.length()).replace('/',
                            '.');
                    String bundlePackage = bundleName.substring(0,
                        bundleName.lastIndexOf('.'));
                    resourceConfig.addResourceBundle(bundleName);
                    resourceConfig.addResourcePackage(bundlePackage);
                }
                continue;
            }
            if (entryName.endsWith(FRENCH_BUNDLE_PROPS))
            {
                resourceConfig.addResourceBundle(entryName.substring(0,
                    entryName.length() - FRENCH_BUNDLE_PROPS.length()).replace('/', '.'));
                continue;
            }
            resourceConfig.addResourcePattern(entryName);
        }
        context.addResourceConfig(resourceConfig);
    }

    @Override
    public void init(Config config)
    {

    }

}
