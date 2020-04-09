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

import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;

import aQute.bnd.annotation.spi.ServiceConsumer;

@ServiceConsumer(value = NativeImageCliBuilder.class)
public interface NativeImageCli
{

    static Optional<NativeImageCli> newInstanceFindNativeImageExecutable(){
        return newInstanceFindNativeImageExecutable(null);
    }

    static Optional<NativeImageCli> newInstanceFindNativeImageExecutable(Path path)
    {
        return internal().findNativeImageExecutable(path);
    }

    static Optional<NativeImageCli> newInstanceFromExecutable(Path exec)
    {
        return internal().fromExecutable(exec);
    }

    private static NativeImageCliBuilder internal()
    {

        final Optional<NativeImageCliBuilder> oNativeImageCliBuilder = ServiceLoader.load(
            NativeImageCliBuilder.class).findFirst();

        if (oNativeImageCliBuilder.isPresent())
        {
            return oNativeImageCliBuilder.get();
        }
        final NativeImageCliBuilder nativeImageCliBuilder = ServiceLoader.load(
            NativeImageCli.class.getModule().getLayer(),
            NativeImageCliBuilder.class).findFirst().orElseThrow(
                () -> new RuntimeException(
                    String.format("ServiceLoader could not find found: %s",
                        NativeImageCliBuilder.class.getName())));
        return nativeImageCliBuilder;
    }

    Path execute(Path executionDir, NativeImageArguments arguments) throws Exception;

    String getVersion();
}
