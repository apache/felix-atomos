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
package org.apache.felix.atomos.tests.index.bundles.b1;

import java.util.Hashtable;
import java.util.Map;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

public class ActivatorBundle1 implements BundleActivator
{
    @Override
    public void start(BundleContext context) throws Exception
    {
        context.registerService(BundleActivator.class, this,
            new Hashtable<>(Map.of("test.activator", this.getClass().getSimpleName(),
                "test.bundle", FrameworkUtil.getBundle(this.getClass()))));
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {
    }
}
