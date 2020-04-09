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
package org.apache.felix.atomos.utils.substrate.impl.json;

import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;

public class ResourceJsonUtil
{

    private static String BUNDLE_NAME = "{ \"name\" : \"%s\" }";
    private static String BUNDLES_END = "]";
    private static String BUNDLES_START = "\"bundles\" : [\n";
    private static String COMMA = ",\n";
    private static String END = "\n}";
    private static char NL = '\n';
    private static String RESOURCE_PATTERN = "{ \"pattern\" : \"%s\" }";
    private static String RESOURCES_END = BUNDLES_END;
    private static String RESOURCES_START = "\"resources\" : [\n";
    private static String START = "{\n";

    private static Object ind(final int num)
    {
        final StringBuilder indent = new StringBuilder();
        for (int i = 0; i < num; i++)
        {
            indent.append("  ");
        }
        return indent.toString();
    }

    public static String json(final ResourceConfiguration result)
    {
        //        if (true)
        //        {
        //            return "{\"resources\" : [ {\"pattern\": \"atomos/.*$\"} ] }";
        //        }

        final TreeSet<String> allResourceBundles = new TreeSet<>(String::compareTo);
        allResourceBundles.addAll(result.getResourceBundles());
        final TreeSet<String> allResourcePatterns = new TreeSet<>(String::compareTo);
        allResourcePatterns.addAll(result.getResourcePatterns());

        final AtomicBoolean first = new AtomicBoolean();
        final StringBuilder resourceConfig = new StringBuilder();
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

}
