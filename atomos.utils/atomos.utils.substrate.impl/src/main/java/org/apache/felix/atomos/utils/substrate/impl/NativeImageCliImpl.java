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

import org.apache.felix.atomos.utils.substrate.api.NativeImageArguments;
import org.apache.felix.atomos.utils.substrate.api.NativeImageCli;

public class NativeImageCliImpl implements NativeImageCli
{

    private Path nativeImageExec;

    private NativeImageCliImpl()
    {

    }

    NativeImageCliImpl(final Path nativeImageExec)
    {
        this();
        this.nativeImageExec = nativeImageExec;
    }

    @Override
    public Path execute(final Path executionDir, final NativeImageArguments arguments)
        throws Exception
    {
        return NativeImageCliUtil.execute(nativeImageExec, executionDir, arguments);
    }

    @Override
    public String getVersion()
    {
        return NativeImageCliUtil.getVersion(nativeImageExec);
    }

}
