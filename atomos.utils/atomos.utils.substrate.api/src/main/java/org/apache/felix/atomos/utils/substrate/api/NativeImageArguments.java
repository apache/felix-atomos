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
package org.apache.felix.atomos.utils.substrate.api;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import aQute.bnd.annotation.spi.ServiceConsumer;

@ServiceConsumer(value = NativeImageArgumentsBuilder.class)
public interface NativeImageArguments
{

    static NativeImageArgumentsBuilder builder()
    {
        final Optional<NativeImageArgumentsBuilder> oargsBuilder = ServiceLoader.load(
            NativeImageArgumentsBuilder.class).findFirst();

        if (oargsBuilder.isPresent())
        {
            return oargsBuilder.get();
        }
        final NativeImageArgumentsBuilder argsBuilder = ServiceLoader.load(
            NativeImageArguments.class.getModule().getLayer(),
            NativeImageArgumentsBuilder.class).findFirst().orElseThrow(
                () -> new RuntimeException(
                    String.format("ServiceLoader could not find found: %s",
                        NativeImageArgumentsBuilder.class.getName())));
        return argsBuilder;
    }

    List<String> arguments();

    String name();
}
