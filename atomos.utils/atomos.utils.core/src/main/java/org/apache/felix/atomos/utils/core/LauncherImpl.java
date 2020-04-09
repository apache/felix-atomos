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
package org.apache.felix.atomos.utils.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.api.Launcher;
import org.apache.felix.atomos.utils.api.RegisterServiceCall;
import org.apache.felix.atomos.utils.api.plugin.BundleActivatorPlugin;
import org.apache.felix.atomos.utils.api.plugin.ClassPlugin;
import org.apache.felix.atomos.utils.api.plugin.ComponentDescription;
import org.apache.felix.atomos.utils.api.plugin.ComponentMetaDataPlugin;
import org.apache.felix.atomos.utils.api.plugin.FileCollectorPlugin;
import org.apache.felix.atomos.utils.api.plugin.FileHandlerPlugin;
import org.apache.felix.atomos.utils.api.plugin.FinalPlugin;
import org.apache.felix.atomos.utils.api.plugin.JarPlugin;
import org.apache.felix.atomos.utils.api.plugin.MethodPlugin;
import org.apache.felix.atomos.utils.api.plugin.RegisterServicepPlugin;
import org.apache.felix.atomos.utils.api.plugin.SubstratePlugin;
import org.apache.felix.atomos.utils.core.scr.mock.EmptyBundeLogger;
import org.apache.felix.atomos.utils.core.scr.mock.PathBundle;
import org.apache.felix.scr.impl.logger.BundleLogger;
import org.apache.felix.scr.impl.metadata.ComponentMetadata;
import org.apache.felix.scr.impl.parser.KXml2SAXParser;
import org.apache.felix.scr.impl.xml.XmlHandler;
import org.osgi.framework.Constants;

public class LauncherImpl implements Launcher
{

    public static List<Class<?>> loadClasses(List<Path> paths, URLClassLoader cl)
    {
        return paths.stream().map(p -> {
            try
            {
                return new JarFile(p.toFile());
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        }).flatMap(j -> j.stream()).filter(e -> !e.isDirectory()).filter(
            e -> e.getName().endsWith(".class")).filter(
                e -> !e.getName().endsWith("module-info.class")).map(e -> {
                    try
                    {
                        String name = e.getName().replace("/", ".").substring(0,
                            e.getName().length() - 6);
                        return cl.loadClass(name);
                    }
                    catch (NoClassDefFoundError | ClassNotFoundException e1)
                    {
                        //   happened when incomplete classpath
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static List<ComponentDescription> readComponentDescription(JarFile jar)
        throws Exception
    {
        BundleLogger logger = new EmptyBundeLogger();
        List<ComponentMetadata> list = new ArrayList<>();
        Attributes attributes = jar.getManifest().getMainAttributes();
        String descriptorLocations = attributes.getValue("Service-Component");// ComponentConstants.SERVICE_COMPONENT);
        if (descriptorLocations != null)
        {
            StringTokenizer st = new StringTokenizer(descriptorLocations, ", ");
            while (st.hasMoreTokens())
            {
                String descriptorLocation = st.nextToken();
                try (
                    InputStream stream = jar.getInputStream(
                        jar.getEntry(descriptorLocation));
                    BufferedReader in = new BufferedReader(
                        new InputStreamReader(stream, "UTF-8")))
                {

                    XmlHandler handler = new XmlHandler(new PathBundle(jar), logger, true,
                        true);

                    KXml2SAXParser parser = new KXml2SAXParser(in);
                    parser.parseXML(handler);
                    list.addAll(handler.getComponentMetadataList());
                }

            }

            //      //Felix SCR Version-> 2.1.17-SNAPSHOT
            //      //https://github.com/apache/felix-dev/commit/2d035e21d69c2bb8892d5d5d3e1027befcc3c50b#diff-dad1c7cc45e5c46bca969c95ac501546
            //      while (st.hasMoreTokens())
            //      {
            //          String descriptorLocation = st.nextToken();
            //          InputStream stream = jar.getInputStream(jar.getEntry(descriptorLocation));
            //          XmlHandler handler = new XmlHandler(new PathBundle(jar), logger, true, true);
            //
            //          final SAXParserFactory factory = SAXParserFactory.newInstance();
            //          factory.setNamespaceAware(true);
            //          final SAXParser parser = factory.newSAXParser();
            //          parser.parse( stream, handler );
            //          list.addAll(handler.getComponentMetadataList());
            //      }
        }

        List<ComponentDescription> cds = list.parallelStream().map(cmd -> {
            cmd.validate();
            return new ComponentDescriptionImpl(cmd);

        }).collect(Collectors.toList());
        return cds;
    }

    private Collection<SubstratePlugin<?>> plugins = null;

    private LauncherImpl()
    {

    }

    LauncherImpl(Collection<SubstratePlugin<?>> plugins)
    {

        this();
        this.plugins = plugins;

    }

    @Override
    public Context execute()
    {
        return execute(new ContextImpl());
    }

    @Override
    public Context execute(Context context)
    {

        //CollectFiles
        orderdPluginsBy(FileCollectorPlugin.class)//
            .peek(System.out::println)//
            .forEachOrdered(plugin -> plugin.collectFiles(context));//

        //Visit all files with type
        orderdPluginsBy(FileHandlerPlugin.class)//
            .peek(System.out::println)//
            .forEachOrdered(plugin -> {

                //for each FileType
                List.of(FileType.values()).forEach(fileType -> {
                    context.getFiles(fileType)//
                        .forEach(path -> plugin.handleFile(context, path, fileType));
                });
            });//

        List<Path> artefacts = context.getFiles(FileType.ARTIFACT).collect(
            Collectors.toList());
        URL[] urls = artefacts.stream().map(p -> {
            try
            {
                return p.toUri().toURL();
            }
            catch (MalformedURLException e1)
            {
                throw new UncheckedIOException(e1);
            }
        }).toArray(URL[]::new);

        try (URLClassLoader classLoader = URLClassLoader.newInstance(urls, null))
        {

            List<Class<?>> classes = loadClasses(artefacts, classLoader);

            List<JarPlugin<?>> jarPlugins = new ArrayList<>();//collector had compile issues on ojdk compiler
            orderdPluginsBy(JarPlugin.class).forEachOrdered(jarPlugins::add);

            jarPlugins.forEach(plugin -> plugin.preJars(context));

            for (Path path : artefacts)
            {
                JarFile jar = new JarFile(path.toFile());
                jarPlugins.forEach(plugin -> plugin.doJar(jar, context, classLoader));

                Attributes attributes = jar.getManifest().getMainAttributes();
                String bundleActivatorClassName = attributes.getValue(
                    org.osgi.framework.Constants.BUNDLE_ACTIVATOR);
                if (bundleActivatorClassName == null)
                {
                    bundleActivatorClassName = attributes.getValue(
                        Constants.EXTENSION_BUNDLE_ACTIVATOR);
                }

                if (bundleActivatorClassName != null)
                {
                    Class<?> bundleActivatorClass;
                    try
                    {
                        bundleActivatorClass = classLoader.loadClass(
                            bundleActivatorClassName.trim());
                        orderdPluginsBy(BundleActivatorPlugin.class)//
                            .peek(System.out::println)//
                            .forEachOrdered(plugin -> plugin.doBundleActivator(
                                bundleActivatorClass, context, classLoader));
                    }
                    catch (ClassNotFoundException e)
                    {
                        e.printStackTrace();
                    }
                }
                try
                {
                    List<ComponentMetaDataPlugin<?>> cmdP = new ArrayList<>();
                    orderdPluginsBy(ComponentMetaDataPlugin.class).forEachOrdered(
                        cmdP::add);
                    List<ComponentDescription> cds = readComponentDescription(jar);
                    for (ComponentDescription cd : cds)
                    {
                        cmdP.forEach(plugin -> {
                            plugin.doComponentMetaData(cd, context, classLoader);
                        });
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                List<RegisterServicepPlugin<?>> rscP = new ArrayList<>();//
                orderdPluginsBy(RegisterServicepPlugin.class).forEachOrdered(rscP::add);
                List<RegisterServiceCall> rscs = context.getRegisterServiceCalls();
                for (RegisterServiceCall rsc : rscs)
                {
                    rscP.forEach(plugin -> {
                        plugin.doRegisterServiceCall(rsc, context, classLoader);
                    });
                }
            }
            jarPlugins.forEach(plugin -> plugin.postJars(context));

            List<ClassPlugin<?>> classPlugins = new ArrayList<>();
            orderdPluginsBy(ClassPlugin.class).forEachOrdered(classPlugins::add);

            List<MethodPlugin<?>> methodPlugins = new ArrayList<>();
            orderdPluginsBy(MethodPlugin.class).forEachOrdered(methodPlugins::add);

            if (!classPlugins.isEmpty() || !methodPlugins.isEmpty())
            {
                for (Class<?> c : classes)
                {
                    classPlugins.forEach(p -> p.doClass(c, context));
                    if (!methodPlugins.isEmpty())
                    {
                        try
                        {
                            Method[] methods = c.getDeclaredMethods();
                            if (methods != null)
                            {
                                for (Method m : methods)
                                {
                                    methodPlugins.forEach(p -> p.doMethod(m, context));
                                }
                            }
                        }
                        catch (NoClassDefFoundError e)
                        {
                            //e.printStackTrace(); //TODO Log
                            System.out.println("incomplete classpath: " + c);
                        }
                    }
                }
            }
            orderdPluginsBy(FinalPlugin.class).forEachOrdered(p -> p.doFinal(context));

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return context;
    }

    List<SubstratePlugin<?>> getPlugins()
    {
        return List.copyOf(plugins);
    }

    private <T extends SubstratePlugin<?>> Stream<T> orderdPluginsBy(Class<T> clazz)
    {
        return plugins.parallelStream()//
            .filter(clazz::isInstance)//
            .map(clazz::cast)//
            .sorted((p1, p2) -> p1.ranking(clazz) - p2.ranking(clazz));
    }
}