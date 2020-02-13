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
package org.apache.felix.atomos.substrate.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

@Component(service = SubstrateService.class, property = { "osgi.command.scope=atomos",
        "osgi.command.function=substrateBundles" })
public class SubstrateService
{
    private static final Collection<String> EXCLUDE_NAMES = Arrays.asList("/about.html",
        "/DEPENDENCIES", "/LICENSE", "/NOTICE", "/changelog.txt", "/LICENSE.txt");
    private static final Collection<String> EXCLUDE_PATHS = Arrays.asList(
        "META-INF/maven/", "OSGI-OPT/");
    private static final String ATOMOS_BUNDLES = "/atomos/";
    private static final String ATOMOS_BUNDLES_INDEX = ATOMOS_BUNDLES + "bundles.index";
    private static final String ATOMOS_BUNDLE = "ATOMOS_BUNDLE";
    @Activate
    private BundleContext context;

    public void substrateBundles(File output) throws IOException
    {
        if (!output.isDirectory())
        {
            throw new IllegalArgumentException("Output file must be a directory.");
        }
        File atomosDir = new File(output, ATOMOS_BUNDLES);
        atomosDir.mkdir();
        List<String> resources = new ArrayList<String>();
        for (Bundle b : context.getBundles())
        {
            File bundleDir = new File(atomosDir, Long.toString(b.getBundleId()));
            resources.add(ATOMOS_BUNDLE);
            resources.add(Long.toString(b.getBundleId()));
            resources.add(b.getSymbolicName());
            resources.add(b.getVersion().toString());
            Enumeration<URL> entries = b.findEntries("/", "*", false);
            while (entries.hasMoreElements())
            {
                URL rootResource = entries.nextElement();
                String rootPath = rootResource.getPath();
                if (rootPath.startsWith("/"))
                {
                    rootPath = rootPath.substring(1);
                }
                // make sure this is not from a fragment
                if (!rootResource.equals(b.getEntry(rootPath)))
                {
                    continue;
                }
                if (!rootPath.endsWith("/"))
                {
                    // skip default package classes
                    if (!rootPath.endsWith(".class"))
                    {
                        resources.add(rootPath);
                        File resourceFile = new File(bundleDir, rootPath);
                        resourceFile.getParentFile().mkdirs();
                        try (InputStream in = rootResource.openStream())
                        {
                            Files.copy(rootResource.openStream(), resourceFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                else if (rootPath.contains("-"))
                {
                    Enumeration<URL> nonPackageEntry = b.findEntries(rootPath, "*", true);
                    while (nonPackageEntry.hasMoreElements())
                    {
                        URL resource = nonPackageEntry.nextElement();
                        String path = resource.getPath();
                        if (path.startsWith("/"))
                        {
                            path = path.substring(1);
                        }
                        // make sure this is not from a fragment
                        if (resource.equals(b.getEntry(path)))
                        {
                            resources.add(path);
                            if (!path.endsWith("/") && !isExcluded(path))
                            {
                                File resourceFile = new File(bundleDir, path);
                                resourceFile.getParentFile().mkdirs();
                                try (InputStream in = resource.openStream())
                                {
                                    Files.copy(resource.openStream(),
                                        resourceFile.toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                                }
                            }
                        }
                    }
                }
            }
        }
        File bundlesIndex = new File(output, ATOMOS_BUNDLES_INDEX);
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(bundlesIndex))))
        {
            resources.forEach((l) -> {
                try
                {
                    writer.append(l).append('\n');
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private boolean isExcluded(String path)
    {
        for (String excludedName : EXCLUDE_NAMES)
        {
            if (path.endsWith(excludedName))
            {
                return true;
            }
        }
        for (String excludedPath : EXCLUDE_PATHS)
        {
            if (path.startsWith(excludedPath))
            {
                return true;
            }
        }
        return false;
    }

}
