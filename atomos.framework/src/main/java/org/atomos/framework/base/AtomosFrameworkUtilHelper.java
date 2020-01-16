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

package org.atomos.framework.base;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.osgi.framework.Bundle;
import org.osgi.framework.connect.FrameworkUtilHelper;

public class AtomosFrameworkUtilHelper implements FrameworkUtilHelper
{
    static private final Set<FrameworkUtilHelper> helpers = new CopyOnWriteArraySet<>();

    static void addHelper(FrameworkUtilHelper helper)
    {
        helpers.add(helper);
    }

    static void removeHelper(FrameworkUtilHelper helper)
    {
        helpers.remove(helper);
    }

    @Override
    public Optional<Bundle> getBundle(Class<?> classFromBundle)
    {
        return helpers.stream().map(h -> h.getBundle(classFromBundle)) //
            .filter(Optional::isPresent) //
            .map(Optional::get) //
            .findFirst();
    }
}
