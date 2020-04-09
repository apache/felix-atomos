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
package org.apache.felix.atomos.utils.substrate.impl.config;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;

public class DefaultDynamicProxyConfiguration implements DynamicProxyConfiguration
{

    private final Set<Set<String>> items = new HashSet<>();

    public DefaultDynamicProxyConfiguration()
    {
    }

    public void addItem(final Set<String> item)
    {
        items.add(item);
    }

    public void addItem(final String... item)
    {
        addItem(Stream.of(item).collect(Collectors.toSet()));
    }

    @Override
    public Set<Set<String>> getItems()
    {
        return items;
    }

}
