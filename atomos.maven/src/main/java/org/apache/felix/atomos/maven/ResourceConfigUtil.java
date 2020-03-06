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
package org.apache.felix.atomos.maven;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ResourceConfigUtil
{
    //TODO: remove Duplicats with substrateService
    private static Collection<String> EXCLUDE_DIRS = Collections.unmodifiableList(
        Arrays.asList("META-INF/", "OSGI-INF/", "OSGI-OPT/"));
    private static Collection<String> EXCLUDE_NAMES = Collections.unmodifiableList(
        Arrays.asList("/packageinfo"));
    private static String SERVICES = "META-INF/services/";
    private static String CLASS_SUFFIX = ".class";
    private static String START = "{\n";
    private static String END = "\n}";
    private static String COMMA = ",\n";
    private static char NL = '\n';
    private static String BUNDLES_START = "\"bundles\" : [\n";
    private static String BUNDLE_NAME = "{ \"name\" : \"%s\" }";
    private static String BUNDLES_END = "]";
    private static String RESOURCES_START = "\"resources\" : [\n";
    private static String RESOURCE_PATTERN = "{ \"pattern\" : \"%s\" }";
    private static String RESOURCES_END = BUNDLES_END;
    private static String FRENCH_BUNDLE_CLASS = "_fr.class";
    private static String FRENCH_BUNDLE_PROPS = "_fr.properties";

    public static ResourceConfigResult resourceConfig(List<Path> paths) throws IOException
    {

        Set<String> allResourceBundles = new TreeSet<>();
        Set<String> allResourcePatterns = new TreeSet<>();
        Set<String> allResourcePackages = new TreeSet<>();
        discoverResources(paths, allResourceBundles, allResourcePatterns,
            allResourcePackages);

        ResourceConfigResult result = new ResourceConfigResult();
        result.allResourceBundles = allResourceBundles;
        result.allResourcePackages = allResourcePackages;
        result.allResourcePatterns = allResourcePatterns;

        return result;
    }

    public static String createResourceJson(ResourceConfigResult result)
    {
        //        if (true)
        //        {
        //            return "{\"resources\" : [ {\"pattern\": \"atomos/.*$\"} ] }";
        //        }

        TreeSet<String> allResourceBundles = new TreeSet<>((o1, o2) -> o1.compareTo(o2));
        allResourceBundles.addAll(result.allResourceBundles);
        TreeSet<String> allResourcePatterns = new TreeSet<>((o1, o2) -> o1.compareTo(o2));
        allResourcePatterns.addAll(result.allResourcePatterns);

        AtomicBoolean first = new AtomicBoolean();
        StringBuilder resourceConfig = new StringBuilder();
        resourceConfig.append(START);
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
        return resourceConfig.toString();

    }

    private static void discoverResources(List<Path> paths,
        Set<String> allResourceBundles, Set<String> allResourcePatterns,
        Set<String> allResourcePackages) throws IOException
    {
        for (Path path : paths)
        {

            try (JarFile jar = new JarFile(path.toFile());)
            {
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
                        allResourcePatterns.add(entryName);
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
                            String bundleName = entryName.substring(0, entryName.length()
                                - FRENCH_BUNDLE_CLASS.length()).replace('/', '.');
                            String bundlePackage = bundleName.substring(0,
                                bundleName.lastIndexOf('.'));
                            allResourceBundles.add(bundleName);
                            allResourcePackages.add(bundlePackage);
                        }
                        continue;
                    }
                    if (entryName.endsWith(FRENCH_BUNDLE_PROPS))
                    {
                        allResourceBundles.add(entryName.substring(0,
                            entryName.length() - FRENCH_BUNDLE_PROPS.length()).replace(
                                '/', '.'));
                        continue;
                    }
                    allResourcePatterns.add(entryName);
                }
            }
        }
    }

    private static Object ind(int num)
    {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < num; i++)
        {
            indent.append("  ");
        }
        return indent.toString();
    }

    static class ResourceConfigResult
    {
        Set<String> allResourceBundles = new TreeSet<>();
        Set<String> allResourcePatterns = new TreeSet<>();
        Set<String> allResourcePackages = new TreeSet<>();
    }
}
