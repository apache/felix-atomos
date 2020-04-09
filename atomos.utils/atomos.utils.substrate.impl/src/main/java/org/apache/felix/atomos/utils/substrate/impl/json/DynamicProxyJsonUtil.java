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

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;

public class DynamicProxyJsonUtil
{
    private static String COMMA = ",";
    private static String END = "]";
    private static String ITEM_PATTERN_EXIST = "\"%s\"";
    private static char NL = '\n';
    private static String START = "[";

    private static Object ind(final int num)
    {
        final StringBuilder indent = new StringBuilder();
        for (int i = 0; i < num; i++)
        {
            indent.append("  ");
        }
        return indent.toString();
    }

    public static String json(final DynamicProxyConfiguration dynamicProxyConfig)
    {
        final AtomicBoolean firstSet = new AtomicBoolean();
        final AtomicBoolean firstItem = new AtomicBoolean();
        final StringBuilder json = new StringBuilder();
        json.append(START);
        for (final Set<String> items : dynamicProxyConfig.getItems())
        {
            if (!firstSet.compareAndSet(false, true))
            {
                json.append(COMMA);
            }
            json.append(NL);
            json.append(ind(1));
            json.append(START);
            for (final String item : items.stream().sorted().collect(Collectors.toSet()))
            {
                if (!firstItem.compareAndSet(false, true))
                {
                    json.append(COMMA);
                }
                json.append(String.format(ITEM_PATTERN_EXIST, item));
            }
            firstItem.set(false);
            json.append(END);
        }
        json.append(NL);
        json.append(END);
        return json.toString();

    }

}
