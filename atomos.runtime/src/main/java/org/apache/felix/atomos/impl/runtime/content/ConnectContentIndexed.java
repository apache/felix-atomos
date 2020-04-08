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
package org.apache.felix.atomos.impl.runtime.content;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.osgi.framework.connect.ConnectContent;

public class ConnectContentIndexed implements ConnectContent
{
    static class URLConnectEntry implements ConnectEntry
    {
        private final String name;
        private final URL resource;

        URLConnectEntry(String name, URL resource)
        {
            this.name = name;
            this.resource = resource;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public long getContentLength()
        {
            try
            {
                return resource.openConnection().getContentLengthLong();
            }
            catch (IOException e)
            {
                return -1;
            }
        }

        @Override
        public long getLastModified()
        {
            try
            {
                return resource.openConnection().getDate();
            }
            catch (IOException e)
            {
                return 0;
            }
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return resource.openStream();
        }

    }

    private final String index;
    private final Set<String> entries;

    public ConnectContentIndexed(String index, List<String> entries)
    {
        this.index = index;
        this.entries = Collections.unmodifiableSet(new LinkedHashSet<>(entries));
    }

    @Override
    public Optional<Map<String, String>> getHeaders()
    {
        return Optional.empty();
    }

    @Override
    public Iterable<String> getEntries() throws IOException
    {
        return entries;
    }

    @Override
    public Optional<ConnectEntry> getEntry(String name)
    {
        if (entries.contains(name))
        {
            String slashName = '/' + name;
            URL resource = getClass().getResource(index + slashName);
            if (resource == null)
            {
                resource = getClass().getResource(slashName);
            }
            if (resource != null)
            {
                return Optional.of(new URLConnectEntry(name, resource));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<ClassLoader> getClassLoader()
    {
        return Optional.of(getClass().getClassLoader());
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

}
