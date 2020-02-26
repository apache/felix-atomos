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
package org.apache.felix.atomos.impl.runtime.base;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.osgi.framework.connect.ConnectContent;

public class FileConnectContent implements ConnectContent
{
    public static class FileConnectEntry implements ConnectEntry
    {
        private final File entry;
        private final String name;

        public FileConnectEntry(File entry, String name)
        {
            this.entry = entry;
            boolean endsInSlash = name.length() > 0
                && name.charAt(name.length() - 1) == '/';
            if (entry.isDirectory())
            {
                if (!endsInSlash)
                    name += '/';
            }
            else if (endsInSlash)
                name = name.substring(0, name.length() - 1);
            this.name = name;
        }

        @Override
        public long getContentLength()
        {
            return entry.length();
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return new FileInputStream(entry);
        }

        @Override
        public long getLastModified()
        {
            return entry.lastModified();
        }

        @Override
        public String getName()
        {
            return name;
        }

    }

    private static final String POINTER_UPPER_DIRECTORY = "..";

    final File root;

    public FileConnectContent(File root)
    {
        this.root = root;
    }

    @Override
    public void open() throws IOException
    {
        // do nothing
    }

    @Override
    public void close() throws IOException
    {
        // do nothing
    }

    @Override
    public Optional<ClassLoader> getClassLoader()
    {
        return Optional.of(getClass().getClassLoader());
    }

    @Override
    public Iterable<String> getEntries() throws IOException
    {
        Path rootPath = root.toPath();
        return Files.find(rootPath, Integer.MAX_VALUE, (p, a) -> true).map((p) -> {
            Path relative = rootPath.relativize(p);
            StringBuilder builder = new StringBuilder();
            for (Path path : relative)
            {
                builder.append(path.getFileName());
                if (Files.isDirectory(path))
                {
                    builder.append('/');
                }
            }
            return builder.toString();
        }).collect(Collectors.toList());
    }

    @Override
    public Optional<ConnectEntry> getEntry(final String name)
    {
        return getFile(name).map((f) -> new FileConnectEntry(f, name));
    }

    private Optional<File> getFile(String path)
    {
        File file = new File(root, path);
        if (!file.exists())
        {
            return Optional.empty();
        }
        if (path.contains(POINTER_UPPER_DIRECTORY))
        {
            try
            {
                if (!file.getCanonicalPath().startsWith(root.getCanonicalPath()))
                {
                    return Optional.empty();
                }
            }
            catch (IOException e)
            {
                return Optional.empty();
            }
        }
        return Optional.of(file);
    }

    @Override
    public Optional<Map<String, String>> getHeaders()
    {
        return Optional.empty();
    }

}
