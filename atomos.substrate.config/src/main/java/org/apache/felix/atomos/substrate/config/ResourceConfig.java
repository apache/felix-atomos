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
package org.apache.felix.atomos.substrate.config;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;

@Component(service = ResourceConfig.class, property = { "osgi.command.scope=atomos",
        "osgi.command.function=resourceConfig" })
public class ResourceConfig
{
    private static final Collection<String> EXCLUDE_DIRS = Collections.unmodifiableList(
        Arrays.asList("META-INF/", "OSGI-INF/", "OSGI-OPT/"));
    private static final Collection<String> EXCLUDE_NAMES = Collections.unmodifiableList(
        Arrays.asList("/packageinfo"));
    private static final String SERVICES = "META-INF/services/";
    private static final String CLASS_SUFFIX = ".class";
    private static final String START = "{\n";
    private static final String END = "\n}";
    private static final String COMMA = ",\n";
    private static final String COMMA_ESCAPE = ",\\\n";
    private static final char NL = '\n';
    private static final String BUNDLES_START = "\"bundles\" : [\n";
    private static final String BUNDLE_NAME = "{ \"name\" : \"%s\" }";
    private static final String BUNDLES_END = "]";
    private static final String RESOURCES_START = "\"resources\" : [\n";
    private static final String RESOURCE_PATTERN = "{ \"pattern\" : \"%s\" }";
    private static final String RESOURCES_END = BUNDLES_END;
    private static final String FRENCH_BUNDLE_CLASS = "_fr.class";
    private static final String FRENCH_BUNDLE_PROPS = "_fr.properties";

    @Reference
    private ServiceComponentRuntime runtime;
    @Activate
    private BundleContext context;

    public void resourceConfig()
    {
        StringBuilder resourceConfig = new StringBuilder();
        resourceConfig.append(START);
        Set<String> allResourceBundles = new TreeSet<>();
        Set<String> allResourcePatterns = new TreeSet<>();
        Set<String> allResourcePackages = new TreeSet<>();
        discoverResources(allResourceBundles, allResourcePatterns, allResourcePackages);
        AtomicBoolean first = new AtomicBoolean();
        if (!allResourceBundles.isEmpty())
        {
            resourceConfig.append(ind(1)).append(BUNDLES_START);
            allResourceBundles.forEach(b -> {
                if (!first.compareAndSet(false, true))
                {
                    resourceConfig.append(COMMA);
                }
                resourceConfig.append(ind(2)).append(String.format(BUNDLE_NAME, b));
            });
            resourceConfig.append(NL).append(ind(1)).append(BUNDLES_END);
        }
        first.set(false);
        if (!allResourcePatterns.isEmpty())
        {
            if (!allResourceBundles.isEmpty())
            {
                resourceConfig.append(COMMA);
            }
            resourceConfig.append(ind(1)).append(RESOURCES_START);
            allResourcePatterns.forEach(p -> {
                if (!first.compareAndSet(false, true))
                {
                    resourceConfig.append(COMMA);
                }
                resourceConfig.append(ind(2)).append(String.format(RESOURCE_PATTERN, p));
            });
            resourceConfig.append(NL).append(ind(1)).append(RESOURCES_END);
        }
        resourceConfig.append(END);
        System.out.println(resourceConfig);

        if (!allResourcePackages.isEmpty())
        {
            StringBuilder initAtBuildTime = new StringBuilder(
                "Args = --initialize-at-build-time=\\\n");
            first.set(false);
            allResourcePackages.forEach((p) -> {
                if (!first.compareAndSet(false, true))
                {
                    initAtBuildTime.append(COMMA_ESCAPE);
                }
                initAtBuildTime.append(p);
            });
            System.out.println();
            System.out.println(initAtBuildTime);
        }
    }

    private void discoverResources(Set<String> allResourceBundles,
        Set<String> allResourcePatterns, Set<String> allResourcePackages)
    {
        for (Bundle b : context.getBundles())
        {
            if (b.equals(context.getBundle()))
            {
                continue;
            }
            pattern: for (String p : getPaths(b))
            {
                if (p.endsWith("/"))
                {
                    continue;
                }
                if (p.indexOf('/') == -1)
                {
                    continue;
                }
                if (p.startsWith(SERVICES))
                {
                    allResourcePatterns.add(p);
                    continue;
                }
                for (String excluded : EXCLUDE_NAMES)
                {
                    if (p.endsWith(excluded))
                    {
                        continue pattern;
                    }
                }
                for (String excluded : EXCLUDE_DIRS)
                {
                    if (p.startsWith(excluded))
                    {
                        continue pattern;
                    }
                }
                if (p.endsWith(CLASS_SUFFIX))
                {
                    // just looking for resource bundle for french as an indicator
                    if (p.endsWith(FRENCH_BUNDLE_CLASS))
                    {
                        String bundleName = p.substring(0,
                            p.length() - FRENCH_BUNDLE_CLASS.length()).replace('/', '.');
                        String bundlePackage = bundleName.substring(0,
                            bundleName.lastIndexOf('.'));
                        allResourceBundles.add(bundleName);
                        allResourcePackages.add(bundlePackage);
                    }
                    continue;
                }
                if (p.endsWith(FRENCH_BUNDLE_PROPS))
                {
                    allResourceBundles.add(
                        p.substring(0, p.length() - FRENCH_BUNDLE_PROPS.length()).replace(
                            '/', '.'));
                    continue;
                }
                allResourcePatterns.add(p);
            }
        }
    }

    private SortedSet<String> getPaths(Bundle b)
    {
        SortedSet<String> paths = new TreeSet<>();
        BundleWiring wiring = b.adapt(BundleWiring.class);
        if (wiring != null)
        {
            wiring.findEntries("/", "*", BundleWiring.FINDENTRIES_RECURSE).forEach(u -> {
                String p = u.getPath();
                if (p.startsWith("/"))
                {
                    p = p.substring(1);
                }
                paths.add(p);
            });
        }
        return paths;
    }

    private Object ind(int num)
    {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < num; i++)
        {
            indent.append("  ");
        }
        return indent.toString();
    }
}
