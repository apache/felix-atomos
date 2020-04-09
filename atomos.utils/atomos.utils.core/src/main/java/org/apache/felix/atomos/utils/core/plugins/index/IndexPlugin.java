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
package org.apache.felix.atomos.utils.core.plugins.index;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.api.IndexInfo;
import org.apache.felix.atomos.utils.api.plugin.JarPlugin;
import org.apache.felix.atomos.utils.core.IndexInfoImpl;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultResourceConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.json.ResourceJsonUtil;
import org.osgi.framework.Constants;

public class IndexPlugin implements JarPlugin<IndexPluginConfig>
{
    private static final String ATOMOS_BUNDLE_SEPARATOR = "ATOMOS_BUNDLE";

    public static final String ATOMOS_BUNDLES_BASE_PATH = "atomos/";
    public static final String ATOMOS_INDEX_FILE_NAME = "bundles.index";
    public static final String ATOMOS_BUNDLES_INDEX = ATOMOS_BUNDLES_BASE_PATH
        + ATOMOS_INDEX_FILE_NAME;

    public static final String ATOMOS_CATH_ALL = "atomos/.*$";

    private static final String ATOMOS_SUBSTRATE_JAR = "atomos.substrate.jar";
    private static final Collection<String> DEFAULT_EXCLUDE_NAMES = Arrays.asList(
        "about.html", "DEPENDENCIES", "LICENSE", "NOTICE", "changelog.txt",
        "LICENSE.txt");
    private static final Collection<String> DEFAULT_EXCLUDE_PATHS = Arrays.asList(
        "META-INF/maven/", "OSGI-OPT/");

    private static boolean filter(JarEntry entry)
    {
        final String path = entry.getName();
        if (entry.isDirectory() || isClass(path))
        {
            return false;
        }
        for (final String excludedPath : DEFAULT_EXCLUDE_PATHS)
        {
            if (path.startsWith(excludedPath))
            {
                return false;
            }
        }
        for (final String excludedName : DEFAULT_EXCLUDE_NAMES)
        {
            if (path.endsWith(excludedName))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isClass(String path)
    {
        return path.endsWith(".class");
    }

    private AtomicLong counter = new AtomicLong();

    JarOutputStream jos;

    private ArrayList<IndexInfo> sis;

    private Path substrateJar;

    private IndexPluginConfig config;

    @Override
    public void doJar(JarFile jar, Context context, URLClassLoader classLoader)
    {
        long id = counter.getAndIncrement();

        IndexInfoImpl info = new IndexInfoImpl();

        Attributes attributes;
        try
        {
            attributes = jar.getManifest().getMainAttributes();
            info.setBsn(attributes.getValue(Constants.BUNDLE_SYMBOLICNAME));
            info.setVersion(attributes.getValue(Constants.BUNDLE_VERSION));
        }
        catch (IOException e1)
        {

            e1.printStackTrace();
            return;
        }

        info.setId(Long.toString(id));

        if (info.getBundleSymbolicName() == null)
        {
            return;
        }
        if (info.getVersion() == null)
        {
            info.setVersion("0.0");
        }
        List<String> files = jar.stream().filter(j -> filter(j)).peek(j -> {
            try
            {
                String fileName = ATOMOS_BUNDLES_BASE_PATH + id + "/" + j.getName();
                if (isJarType())
                {
                    final JarEntry entry = new JarEntry(fileName);
                    if (j.getCreationTime() != null)
                    {
                        entry.setCreationTime(j.getCreationTime());
                    }
                    if (j.getComment() != null)
                    {
                        entry.setComment(j.getComment());
                    }
                    jos.putNextEntry(entry);
                    jos.write(jar.getInputStream(j).readAllBytes());
                }
                else
                {

                    Path path = config.indexOutputDirectory().resolve(fileName);
                    Files.createDirectories(path.getParent());
                    Files.write(path, jar.getInputStream(j).readAllBytes());
                }
            }
            catch (final IOException e)
            {
                throw new UncheckedIOException(e);
            }

        }).map(JarEntry::getName).collect(Collectors.toList());

        info.setFiles(files);
        sis.add(info);
    }

    @Override
    public void init(IndexPluginConfig config)
    {
        this.config = config;
    }

    /**
     * @return
     */
    private boolean isJarType()
    {
        return IndexOutputType.JAR.equals(config.indexOutputType());
    }

    @Override
    public void postJars(Context context)
    {
        try
        {

            final List<String> bundleIndexLines = new ArrayList<>();
            final List<String> resources = new ArrayList<>();
            sis.forEach(s -> {
                if (s.getBundleSymbolicName() != null)
                {
                    bundleIndexLines.add(ATOMOS_BUNDLE_SEPARATOR);
                    bundleIndexLines.add(s.getId());
                    bundleIndexLines.add(s.getBundleSymbolicName());
                    bundleIndexLines.add(s.getVersion());
                    s.getFiles().forEach(f -> {
                        bundleIndexLines.add(f);
                        resources.add(ATOMOS_BUNDLES_BASE_PATH + s.getId() + "/" + f);
                    });
                }
            });
            ByteArrayOutputStream indexBytes;
            try (final ByteArrayOutputStream out = new ByteArrayOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out)))
            {
                indexBytes = out;
                bundleIndexLines.forEach((l) -> {
                    try
                    {
                        writer.append(l).append('\n');
                    }
                    catch (final IOException ex)
                    {
                        throw new UncheckedIOException(ex);
                    }
                });
            }
            writeIndexFile(indexBytes.toByteArray(), context);

            if (isJarType())
            {
                writeGraalResourceConfig(resources, context);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (jos != null)
            {
                try
                {
                    jos.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }

        //after closing
        if (isJarType())
        {
            context.addFile(substrateJar, FileType.INDEX_JAR);
        }
        else
        {
            context.addFile(config.indexOutputDirectory(), FileType.INDEX_DIR);

        }
    }

    @Override
    public void preJars(Context context)
    {

        Path indexOutputDirectory = config.indexOutputDirectory();
        if (!indexOutputDirectory.toFile().isDirectory())
        {
            throw new IllegalArgumentException(
                "Output file must be a directory." + indexOutputDirectory);
        }
        if (!indexOutputDirectory.toFile().exists())
        {
            try
            {
                Files.createDirectories(indexOutputDirectory);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        if (isJarType())
        {
            substrateJar = indexOutputDirectory.resolve(ATOMOS_SUBSTRATE_JAR);
            final Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            try
            {
                jos = new JarOutputStream(new FileOutputStream(substrateJar.toFile()),
                    manifest);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        counter = new AtomicLong(0);
        sis = new ArrayList<>();
    }

    private void writeGraalResourceConfig(List<String> resources, Context context)
        throws IOException
    {
        resources.add(ATOMOS_CATH_ALL); // This alone could be enough,

        DefaultResourceConfiguration rci = new DefaultResourceConfiguration();
        rci.addResourcePattern(resources);
        context.addResourceConfig(rci);

        final String graalResConfJson = ResourceJsonUtil.json(rci);

        final JarEntry graalResConfEntry = new JarEntry(
            "META-INF/native-image/resource-config.json");
        jos.putNextEntry(graalResConfEntry);
        jos.write(graalResConfJson.getBytes());
        jos.flush();

    }

    private void writeIndexFile(final byte[] bytes, Context context) throws IOException
    {
        if (isJarType())
        {
            final JarEntry atomosIndexEntry = new JarEntry(ATOMOS_BUNDLES_INDEX);
            jos.putNextEntry(atomosIndexEntry);
            jos.write(bytes);
        }
        else
        {
            Files.write(config.indexOutputDirectory().resolve(ATOMOS_BUNDLES_INDEX),
                bytes);
        }
        DefaultResourceConfiguration resourceConfig = new DefaultResourceConfiguration();
        resourceConfig.addResourcePattern(ATOMOS_BUNDLES_INDEX);
        context.addResourceConfig(resourceConfig);
    }
}