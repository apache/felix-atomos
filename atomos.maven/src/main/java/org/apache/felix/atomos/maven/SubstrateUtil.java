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
package org.apache.felix.atomos.maven;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.maven.ResourceConfigUtil.ResourceConfigResult;
import org.osgi.framework.Constants;

public class SubstrateUtil
{

    /**
     *
     */
    private static final String ATOMOS_CATH_ALL = "atomos/.*$";
    /**
     *
     */
    private static final String ATOMOS_SUBSTRATE_JAR = "atomos.substrate.jar";
    private static final Collection<String> DEFAULT_EXCLUDE_NAMES = Arrays.asList(
        "about.html", "DEPENDENCIES", "LICENSE", "NOTICE", "changelog.txt",
        "LICENSE.txt");
    private static final Collection<String> DEFAULT_EXCLUDE_PATHS = Arrays.asList(
        "META-INF/maven/", "OSGI-OPT/");
    public static final String ATOMOS_BUNDLES_BASE_PATH = "atomos/";
    public static final String ATOMOS_BUNDLES_INDEX = ATOMOS_BUNDLES_BASE_PATH
        + "bundles.index";
    private static final String ATOMOS_BUNDLE_SEPARATOR = "ATOMOS_BUNDLE";

    enum EntryType
    {
        PACKAGE, NON_PACKAGE, PACKAGE_CLASS, PACKAGE_RESOURCE, DEFAULT_PACKAGE_CLASS, DEFAULT_PACKAGE_RESOURCE, NON_PACKAGE_RESOURCE
    }

    private static boolean isClass(String path)
    {
        return path.endsWith(".class");
    }

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

    public static void indexContent(List<Path> files,
        Path outputRoot)
        throws IOException, NoSuchAlgorithmException
    {
        if (!Files.exists(outputRoot))
        {
            Files.createDirectories(outputRoot);
        }
        if (!Files.isDirectory(outputRoot))
        {
            throw new IllegalArgumentException(
                "Output file must be a directory." + outputRoot);
        }

        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        Files.createDirectory(outputRoot.resolve("META-INF/"));
        try (OutputStream mf = Files.newOutputStream(
            outputRoot.resolve("META-INF/MANIFEST.MF")))
        {
            manifest.write(mf);
        }

        final List<String> bundleIndexLines = new ArrayList<>();
        final List<String> resources = new ArrayList<>();
        final AtomicLong counter = new AtomicLong(0);
        final Stream<SubstrateInfo> bis = files.stream()//
            .map(path -> create(outputRoot, counter.getAndIncrement(), path));

        bis.forEach(s -> {
            if (s.bsn != null)
            {
                bundleIndexLines.add(ATOMOS_BUNDLE_SEPARATOR);
                bundleIndexLines.add(s.id);
                bundleIndexLines.add(s.bsn);
                bundleIndexLines.add(s.version);
                s.files.forEach(f -> {
                    bundleIndexLines.add(f);
                    resources.add(ATOMOS_BUNDLES_BASE_PATH + s.id + "/" + f);
                });
            }
        });
        writeBundleIndexFile(outputRoot, bundleIndexLines);
        writeGraalResourceConfig(outputRoot, resources);
    }

    private static void writeGraalResourceConfig(
        Path root,
        List<String> resources) throws IOException
    {
        //        resources.add(ATOMOS_BUNDLES_INDEX);
        resources.add(ATOMOS_CATH_ALL); // This alone could be enough,

        final ResourceConfigResult result = new ResourceConfigResult();
        result.allResourcePatterns.addAll(resources);

        final String graalResConfJson = ResourceConfigUtil.createResourceJson(result);

        final Path graalResConfEntry = root.resolve(
            "META-INF/native-image/resource-config.json");
        Files.createDirectories(graalResConfEntry.getParent());
        Files.writeString(graalResConfEntry, graalResConfJson);
    }

    private static void writeBundleIndexFile(
        Path root,
        final List<String> resources) throws IOException
    {

        final Path atomosIndexEntry = root.resolve(ATOMOS_BUNDLES_INDEX);
        Files.createDirectories(atomosIndexEntry.getParent());

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out)))
        {
            resources.forEach((l) -> {
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
        Files.write(atomosIndexEntry, out.toByteArray());
    }

    static SubstrateInfo create(Path root, long id, Path path)
    {
        final SubstrateInfo info = new SubstrateInfo();
        info.path = path;
        try (final JarFile jar = new JarFile(info.path.toFile()))
        {
            final Attributes attributes = jar.getManifest().getMainAttributes();
            info.bsn = attributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
            info.version = attributes.getValue(Constants.BUNDLE_VERSION);
            info.id = Long.toString(id);

            if (info.bsn == null)
            {
                return info;
            }
            if (info.version == null)
            {
                info.version = "0.0";
            }
            info.files = jar.stream().filter(j -> filter(j)).peek(j -> {
                try
                {
                    final Path entry = root.resolve(
                        ATOMOS_BUNDLES_BASE_PATH + id + "/" + j.getName());
                    if (j.getCreationTime() != null)
                    {
                        Files.setLastModifiedTime(entry, j.getCreationTime());
                    }
                    if (j.getComment() != null)
                    {
                        // TODO figure out if there is a comment attribute
                    }
                    Files.createDirectories(entry.getParent());
                    Files.copy(jar.getInputStream(j), entry);
                }
                catch (final IOException e)
                {
                    throw new UncheckedIOException(e);
                }

            }).map(JarEntry::getName).collect(Collectors.toList());
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException(e);
        }
        return info;
    }

    static class SubstrateInfo
    {
        Path out;
        Path path;
        String id;
        String bsn;
        String version;
        List<String> files = new ArrayList<>();

    }
}
