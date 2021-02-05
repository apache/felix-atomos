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
package org.apache.felix.atomos.impl.content;

import static org.apache.felix.atomos.impl.base.AtomosBase.sneakyThrow;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.ZipFile;

public class ConnectContentCloseableJar extends ConnectContentJar
{


    static class ZipFileHolder implements Supplier<ZipFile>, Consumer<Supplier<ZipFile>>
    {
        private final Supplier<File> rootSupplier;
        private final String fileName;

        public ZipFileHolder(String fileName, Supplier<File> rootSupplier)
        {
            this.fileName = fileName;
            this.rootSupplier = rootSupplier;
        }

        private volatile ZipFile zipFile;

        @Override
        public void accept(Supplier<ZipFile> s)
        {
            if (s != this)
            {
                return;
            }
            ZipFile current = zipFile;
            if (current != null)
            {
                zipFile = null;
                try
                {
                    current.close();
                }
                catch (IOException e)
                {
                    sneakyThrow(e);
                }
            }
        }

        @Override
        public ZipFile get()
        {
            ZipFile current = zipFile;
            if (current == null)
            {
                try
                {
                    current = zipFile = new ZipFile(
                        new File(rootSupplier.get(), fileName));
                }
                catch (IOException e)
                {
                    sneakyThrow(e);
                }
            }
            return current;
        }

    }

    public ConnectContentCloseableJar(String fileName, Supplier<File> rootSupplier)
    {
        super(new ZipFileHolder(fileName, rootSupplier),
            z -> ((ZipFileHolder) z).accept(z));
    }
}
