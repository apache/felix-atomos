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
package org.apache.felix.atomos.utils.substrate.impl;

import java.nio.file.Path;
import java.util.Optional;

import org.apache.felix.atomos.utils.substrate.api.NativeImageCli;
import org.apache.felix.atomos.utils.substrate.api.NativeImageCliBuilder;

@aQute.bnd.annotation.spi.ServiceProvider(NativeImageCliBuilder.class)
public class NativeImageCliBuilderImpl implements NativeImageCliBuilder
{

    public NativeImageCliBuilderImpl()
    {
    }

    @Override
    public Optional<NativeImageCli> findNativeImageExecutable()
    {
        return findNativeImageExecutable(null);
    }

    @Override
    public Optional<NativeImageCli> findNativeImageExecutable(final Path exec)
    {
        final Optional<Path> oExecPath = NativeImageCliUtil.findNativeImageExecutable(
            exec);
        if (oExecPath.isEmpty())
        {
            return Optional.empty();
        }
        return Optional.of(new NativeImageCliImpl(oExecPath.get()));
    }

    @Override
    public Optional<NativeImageCli> fromExecutable(final Path exec)
    {
        final String version = NativeImageCliUtil.getVersion(exec);

        if (version == null)
        {
            return Optional.empty();
        }

        return Optional.of(new NativeImageCliImpl(exec));
    }

}