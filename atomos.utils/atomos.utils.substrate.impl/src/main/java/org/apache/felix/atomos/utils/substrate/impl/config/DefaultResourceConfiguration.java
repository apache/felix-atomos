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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;

public class DefaultResourceConfiguration implements ResourceConfiguration
{
    private final List<String> resourceBundles = new ArrayList<>();
    private final List<String> resourcePackages = new ArrayList<>();
    private final List<String> resourcePatterns = new ArrayList<>();

    public void addResourceBundle(final Collection<String> resourceBundles)
    {
        this.resourceBundles.addAll(resourceBundles);
    }

    public void addResourceBundle(final String resourceBundle)
    {
        resourceBundles.add(resourceBundle);
    }

    public void addResourcePackage(final Collection<String> resourcePackages)
    {
        this.resourcePackages.addAll(resourcePackages);
    }

    public void addResourcePackage(final String resourcePackage)
    {
        resourcePackages.add(resourcePackage);
    }

    public void addResourcePattern(final Collection<String> resourcePatterns)
    {
        this.resourcePatterns.addAll(resourcePatterns);
    }

    public void addResourcePattern(final String resourcePattern)
    {
        resourcePatterns.add(resourcePattern);
    }

    @Override
    public List<String> getResourceBundles()
    {
        return resourceBundles;
    }

    @Override
    public List<String> getResourcePackages()
    {
        return resourcePackages;
    }

    @Override
    public List<String> getResourcePatterns()
    {
        return resourcePatterns;
    }
}