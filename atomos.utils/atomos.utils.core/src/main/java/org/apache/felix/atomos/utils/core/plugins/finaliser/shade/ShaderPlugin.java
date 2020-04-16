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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.api.plugin.FinalPlugin;

public class ShaderPlugin implements FinalPlugin<ShadeConfig>
{
    //TODO: mainfest - main class and other entrys

    private ShadeConfig config;

    List<ShadePreHolder> candidates = new ArrayList<>();

    Map<JarFile, Path> map = new HashMap<>();

    ShadePreHolder compute(String name)
    {
        return candidates.stream().filter(
            c -> name.equals(c.getName())).findAny().orElseGet(() -> {
                ShadePreHolder s = new ShadePreHolder(name);
                candidates.add(s);
                return s;
            });

    }

    @Override
    public void doFinal(Context context)
    {
        Path shadesJar = config.shadeOutputDirectory().resolve("shaded.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(new Attributes.Name("Created-By"),
            "atomos-maven-plugin");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "foooBar");

        try (JarOutputStream jarOutputStream = new JarOutputStream(
            new FileOutputStream(shadesJar.toFile()), manifest))
        {

            List<Path> classpath = context.getFiles(FileType.ARTIFACT,
                FileType.INDEX_JAR);
            classpath.forEach(p -> {
                try
                {
                    JarFile jar = new JarFile(p.toFile());
                    map.put(jar, p);
                    jar.stream()//
                    .filter(Predicate.not(JarEntry::isDirectory))//
                    .forEach(je -> {
                        compute(je.getName()).add(jar);
                    });
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
            candidates.forEach(c -> {
                try
                {
                    if ("module-info.class".equals(c.getName()))
                    {
                        return;
                    }
                    if ("META-INF/MANIFEST.MF".equals(c.getName()))
                    {
                        return;
                    }
                    JarEntry jarEntry = new JarEntry(c.getName());
                    jarOutputStream.putNextEntry(jarEntry);
                    if (c.getName().startsWith("services"))
                    {
                        AtomicBoolean ab = new AtomicBoolean();
                        StringBuilder sb = new StringBuilder();
                        for (JarFile jar : c.all())
                        {
                            if (ab.getAndSet(true))
                            {
                                sb.append("\n");
                            }
                            JarEntry e = jar.getJarEntry(c.getName());
                            InputStream is = jar.getInputStream(e);
                            sb.append(is);
                        }
                        jarOutputStream.write(sb.toString().getBytes());
                    }
                    else
                    {
                        if (!c.allSameChecksum())
                        {
                            System.out.println(c.getName());
                            c.getSource().forEach((k, v) -> {

                                System.out.println("- Checksum: " + k);
                                System.out.println("-- List of Jars:");

                                v.stream().map(map::get)//
                                .map(Path::toAbsolutePath)//
                                .map(p -> "-- " + p.toString())//
                                .forEach(System.out::println);

                            });
                        }
                        JarFile j = c.any();
                        JarEntry e = j.getJarEntry(c.getName());
                        InputStream is = j.getInputStream(e);
                        jarOutputStream.write(is.readAllBytes());
                    }
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }

            });

        }
        catch (

            Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            map.forEach((j, p) -> {
                try
                {
                    j.close();
                }
                catch (IOException e)
                {
                }
            });
        }
    }

    @Override
    public void init(ShadeConfig config)
    {
        this.config = config;
    }
}