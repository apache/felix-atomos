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

package org.apache.felix.atomos.utils.core.plugins.finaliser.shade;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class ShadePreHolder
{

    private String name;

    private final Map<Long, List<JarFile>> source = new HashMap<>();

    private ShadePreHolder()
    {
    }

    public ShadePreHolder(String name)
    {
        this();
        this.name = name;
    }

    void add(JarFile jarFile)
    {
        JarEntry entry = jarFile.getJarEntry(name);
        byte[] bytes;
        try
        {
            bytes = jarFile.getInputStream(entry).readAllBytes();
        }
        catch (IOException e)
        {
            bytes = new byte[] {};
        }

        Checksum checksum = new Adler32();
        checksum.update(bytes, 0, bytes.length);
        long checksumVal = checksum.getValue();
        List<JarFile> list = source.computeIfAbsent(checksumVal,
            (k) -> new ArrayList<>());
        list.add(jarFile);
    }

    List<JarFile> all()
    {
        return source.entrySet().parallelStream().flatMap(
            c -> c.getValue().stream()).collect(Collectors.toList());
    }

    boolean allSameChecksum()
    {
        return source.size() == 1;
    }

    JarFile any()
    {
        return source.entrySet().parallelStream().map(
            c -> c.getValue().stream().findAny()).findAny().get().get();
    }

    public String getName()
    {
        return name;
    }

    public Map<Long, List<JarFile>> getSource()
    {
        return Map.copyOf(source);
    }

    long size()
    {
        return source.entrySet().parallelStream().flatMap(
            c -> c.getValue().stream()).count();
    }

}
