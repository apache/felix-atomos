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
package org.apache.felix.atomos.utils.api;

import java.util.Optional;
import java.util.ServiceLoader;

import aQute.bnd.annotation.spi.ServiceConsumer;

@ServiceConsumer(LauncherBuilder.class)
public interface Launcher
{
    String SYS_PROP_PLUGINS = "org.apache.felix.atomos.substrate.plugins";
    String SYS_PROP_SEPARATOR = ";";

    static LauncherBuilder builder()
    {
        final Optional<LauncherBuilder> oLauncherBuilder = ServiceLoader.load(
            LauncherBuilder.class).findFirst();

        if (oLauncherBuilder.isPresent())
        {
            return oLauncherBuilder.get();
        }

        LauncherBuilder launcherBuilder = ServiceLoader.load(
            Launcher.class.getModule().getLayer(),
            LauncherBuilder.class).findFirst().orElseThrow(
                () -> new RuntimeException(
                    String.format("ServiceLoader could not find found: %s",
                        LauncherBuilder.class.getName())));

        return launcherBuilder;

    }

    Context execute();

    Context execute(Context context);
}
