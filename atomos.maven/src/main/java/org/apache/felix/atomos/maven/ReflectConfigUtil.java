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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.maven.reflect.ConstructorConfig;
import org.apache.felix.atomos.maven.reflect.MethodConfig;
import org.apache.felix.atomos.maven.reflect.ReflectConfig;
import org.apache.felix.atomos.maven.scrmock.EmptyBundeLogger;
import org.apache.felix.atomos.maven.scrmock.PathBundle;
import org.apache.felix.scr.impl.logger.BundleLogger;
import org.apache.felix.scr.impl.metadata.ComponentMetadata;
import org.apache.felix.scr.impl.metadata.ReferenceMetadata;
import org.apache.felix.scr.impl.parser.KXml2SAXParser;
import org.apache.felix.scr.impl.xml.XmlHandler;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentConstants;

public class ReflectConfigUtil
{
    private static final String OSGI_COMMAND_FUNCTION = "osgi.command.function";

    private static String CLASS_START = "{\n";
    private static String CLASS_END = "}";
    private static String COMMA_SPACE = ", ";
    private static String COMMA_ENTER = ",\n";
    private static String NAME_PATTERN = "\"name\":\"%s\"";
    private static String CLASS_NAME = NAME_PATTERN;
    private static String FIELDS_START = "\"fields\" : [\n";
    private static String FIELD_NAME = NAME_PATTERN;
    private static String FIELDS_END = "]";
    private static String METHODS_START = "\"methods\" : [\n";
    private static String METHOD_NAME = NAME_PATTERN;
    private static String METHODS_END = FIELDS_END;
    private static String CONSTRUCTOR_METHOD_NAME = "<init>";

    private static String PARAMETER_TYPE = "\"parameterTypes\":[%s]";

    private static String COMPONENT_CONSTRUCTOR = "\"allPublicConstructors\" : true";

    public static List<ReflectConfig> reflectConfig(List<Path> paths) throws Exception
    {
        URL[] urls = paths.stream().map(p -> {
            try
            {
                return p.toUri().toURL();
            }
            catch (MalformedURLException e1)
            {
                throw new UncheckedIOException(e1);
            }
        }).toArray(URL[]::new);

        try (URLClassLoader cl = URLClassLoader.newInstance(urls, null))
        {

            List<Class<?>> classes = loadClasses(paths, cl);

            List<ReflectConfig> reflectConfigs = new ArrayList<>();
            for (Path p : paths)
            {
                try (JarFile jar = new JarFile(p.toFile()))
                {
                    discoverBundleActivators(cl, jar, reflectConfigs);
                    discoverSeriviceComponents(cl, jar, reflectConfigs);
                    discoverDTOs(classes, reflectConfigs);

                }
            }
            return reflectConfigs;
        }
    }

    private static List<Class<?>> loadClasses(List<Path> paths, URLClassLoader cl)
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
                }).collect(Collectors.toList());
    }

    private static void discoverDTOs(final List<Class<?>> classes,
        List<ReflectConfig> reflectConfigs)
    {
        classes.stream().filter(c -> c != null).filter(c -> {
            Class<?> clazz = c;
            while (clazz != null && clazz != Object.class)
            {

                if ("org.osgi.dto.DTO".equals(clazz.getName()))
                {
                    return true;
                }
                clazz = clazz.getSuperclass();
            }
            return false;
        }).forEach(clazz -> {

            for (Field field : clazz.getFields())
            {
                addField(field.getName(), clazz, reflectConfigs);
            }
        });

    }

    public static String json(ReflectConfig reflectConfig)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(ind(1)).append(CLASS_START);

        builder.append(ind(2)).append(String.format(CLASS_NAME, reflectConfig.className));

        AtomicReference<String> comma = new AtomicReference<>("");
        if (!reflectConfig.fields.isEmpty())
        {
            builder.append(COMMA_ENTER).append(ind(2)).append(FIELDS_START);
            reflectConfig.fields.forEach(
                f -> builder.append(comma.getAndSet(COMMA_ENTER)).append(ind(3)).append(
                    "{").append(String.format(FIELD_NAME, f)).append("}"));
            builder.append('\n').append(ind(2)).append(FIELDS_END);
        }

        comma.set("");
        if (!reflectConfig.methods.isEmpty() || !reflectConfig.constructor.isEmpty())
        {
            builder.append(COMMA_ENTER).append(ind(2)).append(METHODS_START);
            reflectConfig.constructor.forEach(c -> {
                builder.append(comma.getAndSet(COMMA_ENTER)).append(ind(3)).append(
                    "{").append(String.format(METHOD_NAME, CONSTRUCTOR_METHOD_NAME));

                if (c.methodParameterTypes != null)
                {
                    String types = Stream.of(c.methodParameterTypes).sequential().collect(
                        Collectors.joining("\",\""));
                    if (!types.isEmpty())
                    {
                        types = "\"" + types + "\"";
                    }
                    builder.append(COMMA_SPACE).append(
                        String.format(PARAMETER_TYPE, types));
                }
                builder.append("}");
            });
            reflectConfig.methods.forEach(m -> {
                builder.append(comma.getAndSet(COMMA_ENTER))//
                .append(ind(3)).append("{").append(
                    String.format(METHOD_NAME, m.name));
                if (m.methodParameterTypes != null)
                {
                    String types = Stream.of(m.methodParameterTypes).sequential().collect(
                        Collectors.joining("\",\""));
                    if (!types.isEmpty())
                    {
                        types = "\"" + types + "\"";
                    }
                    builder.append(COMMA_SPACE).append(
                        String.format(PARAMETER_TYPE, types));
                }
                builder.append("}");
            });
            builder.append('\n').append(ind(2)).append(METHODS_END);
        }

        builder.append('\n').append(ind(1)).append(CLASS_END);

        return builder.toString();
    }

    private static Object ind(int num)
    {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < num; i++)
        {
            indent.append("  ");
        }
        return indent.toString();
    }

    public static String json(List<ReflectConfig> reflectConfigs)
    {
        StringBuilder builder = new StringBuilder();
        builder.append('[').append('\n');
        AtomicReference<String> comma = new AtomicReference<>("");

        Collections.sort(reflectConfigs, new Comparator<ReflectConfig>()
        {
            @Override
            public int compare(ReflectConfig o1, ReflectConfig o2)
            {
                return o1.className.compareTo(o2.className);
            }
        });

        for (ReflectConfig config : reflectConfigs)
        {
            builder.append(comma.getAndSet(COMMA_ENTER)).append(json(config));
        }
        builder.append('\n').append(']');
        return builder.toString();
    }

    private static void discoverBundleActivators(URLClassLoader cl, JarFile jar,
        List<ReflectConfig> reflectConfigs) throws IOException
    {

        Attributes attributes = jar.getManifest().getMainAttributes();
        String bundleActivatorClassName = attributes.getValue(Constants.BUNDLE_ACTIVATOR);
        if (bundleActivatorClassName == null)
        {
            bundleActivatorClassName = attributes.getValue(
                Constants.EXTENSION_BUNDLE_ACTIVATOR);
        }
        if (bundleActivatorClassName != null)
        {
            bundleActivatorClassName = bundleActivatorClassName.trim();
            try
            {
                Class<?> bundleActivatorClazz = cl.loadClass(bundleActivatorClassName);
                Class<?> bundleContextClass = cl.loadClass(
                    "org.osgi.framework.BundleContext");
                ReflectConfig rc = computeIfAbsent(reflectConfigs,
                    bundleActivatorClassName);
                rc.constructor.add(new ConstructorConfig(new String[] {}));

                addMethod("start", new Class[] { bundleContextClass },
                    bundleActivatorClazz, reflectConfigs);
                addMethod("stop", new Class[] { bundleContextClass },
                    bundleActivatorClazz, reflectConfigs);
                magic(cl, bundleActivatorClazz, reflectConfigs);
            }
            catch (ClassNotFoundException e)
            {
                // TODO log
            }
        }
    }

    private static ReflectConfig computeIfAbsent(List<ReflectConfig> reflectConfigs,
        final String activator)
    {
        Optional<ReflectConfig> oConfig = reflectConfigs.stream().filter(
            c -> c.className.equals(activator)).findFirst();

        ReflectConfig rc = null;
        if (oConfig.isPresent())
        {
            rc = oConfig.get();
        }
        else
        {
            rc = new ReflectConfig(activator);
            reflectConfigs.add(rc);
        }
        return rc;
    }

    private static void magic(URLClassLoader cl, Class<?> bundleActivatorClass,
        List<ReflectConfig> reflectConfigs)
    {
        try
        {

            Object o = bundleActivatorClass.newInstance();

            Method startMethod = null;
            for (Method m : bundleActivatorClass.getMethods())
            {
                if (m.getName().equals("start") && m.getReturnType().equals(void.class)
                    && m.getParameterCount() == 1
                    && m.getParameters()[0].getParameterizedType().getTypeName().equals(
                        "org.osgi.framework.BundleContext"))
                {
                    startMethod = m;
                    break;
                }
            }

            if (startMethod != null)
            {
                InvocationHandler invocationHandler = new InvocationHandler()
                {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args)
                        throws Throwable
                    {
                        try
                        {
                            //System.out.println("----------------------------------");
                            //System.out.println(method);
                            if (method.getName().equals("registerService")
                                && method.getParameterCount() == 3
                                && method.getParameters()[1].getType().getTypeName().equals(
                                    "java.lang.Object")
                                && method.getParameters()[2].getType().getTypeName().equals(
                                    "java.util.Dictionary"))
                            {
                                Object service = args[1];
                                Dictionary<?, ?> dict = (Dictionary<?, ?>) args[2];
                                //System.out.println("service: "+service.getClass());
                                //System.out.println("cfg: "+dict);
                                if (dict != null)
                                {
                                    Map<?, ?> dictCopy = Collections.list(
                                        dict.keys()).stream().collect(
                                            Collectors.toMap(Function.identity(),
                                                dict::get));
                                    if (dictCopy.containsKey("osgi.command.function"))
                                    {
                                        String[] functions = (String[]) dict.get(
                                            "osgi.command.function");

                                        addMethodsFromGogoCommand(reflectConfigs,
                                            service.getClass(), functions);
                                    }
                                }
                            }
                            if (method.getName().equals("getBundleId"))
                            {
                                return 1L;
                            }
                            if (method.getName().equals("getVersion"))
                            {
                                return cl.loadClass(
                                    "org.osgi.framework.Version").getConstructor(
                                        int.class, int.class, int.class).newInstance(0, 0,
                                            0);
                            }
                            else if (method.getReturnType().isInterface())
                            {
                                return Proxy.newProxyInstance(cl,
                                    new Class[] { method.getReturnType() }, this);
                            }
                        }
                        catch (Exception e)
                        {
                            // expected
                            //e.printStackTrace();
                        }
                        return null;
                    }
                };
                Object p = Proxy.newProxyInstance(cl,
                    new Class[] { cl.loadClass("org.osgi.framework.BundleContext") },
                    invocationHandler);
                startMethod.invoke(o, p);
            }
        }
        catch (Throwable e)
        { // expected
            // TODO Log and maybe show cp errors
            // e.printStackTrace();
        }

    }

    private static void discoverSeriviceComponents(URLClassLoader cl, JarFile jar,
        List<ReflectConfig> reflectConfigs) throws Exception
    {

        List<ComponentMetadata> cDDTOs = readCDDTO(jar);

        for (ComponentMetadata c : cDDTOs)
        {
            c.validate();
            Class<?> clazz = cl.loadClass(c.getImplementationClassName());
            // Activate Deactivate Modify
            Optional.ofNullable(c.getActivate()).ifPresent(
                (m) -> addMethod(m, clazz, reflectConfigs));
            Optional.ofNullable(c.getModified()).ifPresent(
                (m) -> addMethod(m, clazz, reflectConfigs));
            Optional.ofNullable(c.getDeactivate()).ifPresent(
                (m) -> addMethod(m, clazz, reflectConfigs));
            if (c.getActivationFields() != null)
            {
                for (String fName : c.getActivationFields())
                {
                    addField(fName, clazz, reflectConfigs);
                }
            }
            //Reference
            if (c.getDependencies() != null)
            {

                String[] constrParams = new String[c.getNumberOfConstructorParameters()];
                for (ReferenceMetadata r : c.getDependencies())
                {
                    Optional.ofNullable(r.getParameterIndex()).ifPresent(
                        (i) -> constrParams[i] = r.getInterface());
                    Optional.ofNullable(r.getField()).ifPresent(
                        (f) -> addField(f, clazz, reflectConfigs));
                    Optional.ofNullable(r.getBind()).ifPresent(
                        (m) -> addMethod(m, clazz, reflectConfigs));
                    Optional.ofNullable(r.getUpdated()).ifPresent(
                        (m) -> addMethod(m, clazz, reflectConfigs));
                    Optional.ofNullable(r.getUnbind()).ifPresent(
                        (m) -> addMethod(m, clazz, reflectConfigs));
                    Optional.ofNullable(r.getInterface()).ifPresent(
                        (i) -> computeIfAbsent(reflectConfigs, i));
                }
                ReflectConfig config = computeIfAbsent(reflectConfigs, clazz.getName());

                boolean foundConstructor = false;
                if (c.getNumberOfConstructorParameters() == 0)
                {
                    config.constructor.add(new ConstructorConfig());
                    foundConstructor = true;
                }
                else
                {
                    for (Constructor<?> constructor : clazz.getConstructors())
                    {
                        if (constructor.getParameterCount() != c.getNumberOfConstructorParameters())
                        {

                            continue;
                        }
                        boolean match = true;
                        for (int j = 0; j < constructor.getParameterCount(); j++)
                        {
                            String p = constructor.getParameters()[j].getType().getName();
                            String s = constrParams[j];
                            if (s != null && !p.equals(s))
                            {
                                match = false;
                                break;
                            }
                        }
                        if (match)
                        {
                            String[] ps = Stream.of(constructor.getParameters()).map(
                                p -> p.getType().getName()).toArray(String[]::new);
                            config.constructor.add(new ConstructorConfig(ps));
                            foundConstructor = true;
                            break;
                        }
                    }
                    if (!foundConstructor)
                    {
                        config.allPublicConstructors = true;
                    }
                }
            }
            // Gogo osgi.command.function
            if (c.getProperties().containsKey(OSGI_COMMAND_FUNCTION))
            {
                Object oFunctions = c.getProperties().get(OSGI_COMMAND_FUNCTION);
                String[] functions = null;
                if (oFunctions instanceof String[])
                {
                    functions = (String[]) oFunctions;
                }
                else
                {
                    functions = new String[] { oFunctions.toString() };
                }
                addMethodsFromGogoCommand(reflectConfigs, clazz, functions);
            }
        }
    }

    private static void addMethodsFromGogoCommand(List<ReflectConfig> classes,
        Class<?> clazz, String[] functions)
    {
        Class<?> tmpClass = clazz;

        for (String function : functions)
        {
            addMethod(function, tmpClass, classes);
        }
        tmpClass = tmpClass.getSuperclass();

        if (tmpClass != null && !tmpClass.equals(Object.class))
        {
            addMethodsFromGogoCommand(classes, tmpClass, functions);
        }
    }

    private static List<ComponentMetadata> readCDDTO(JarFile jar) throws Exception
    {

        BundleLogger logger = new EmptyBundeLogger();
        List<ComponentMetadata> list = new ArrayList<>();
        Attributes attributes = jar.getManifest().getMainAttributes();
        String descriptorLocations = attributes.getValue(
            ComponentConstants.SERVICE_COMPONENT);
        if (descriptorLocations == null)
        {
            return list;
        }
        StringTokenizer st = new StringTokenizer(descriptorLocations, ", ");
        while (st.hasMoreTokens())
        {
            String descriptorLocation = st.nextToken();
            InputStream stream = jar.getInputStream(jar.getEntry(descriptorLocation));
            BufferedReader in = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"));
            XmlHandler handler = new XmlHandler(new PathBundle(jar), logger, true, true);


            KXml2SAXParser parser = new KXml2SAXParser(in);
            parser.parseXML(handler);
            list.addAll(handler.getComponentMetadataList());
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

        return list;
    }

    private static void addMethod(String mName, Class<?> clazz,
        List<ReflectConfig> classes)
    {
        addMethod(mName, null, clazz, classes);
    }

    //parameterTypes==null means not Set
    //parameterTypes=={} means no method parameter
    private static void addMethod(String mName, Class<?>[] parameterTypes, Class<?> clazz,
        List<ReflectConfig> reflectConfigs)
    {
        for (Method m : clazz.getDeclaredMethods())
        {
            if (mName.equals(m.getName()))
            {
                if (parameterTypes == null)
                {
                    ReflectConfig config = computeIfAbsent(reflectConfigs,
                        clazz.getName());
                    config.methods.add(new MethodConfig(mName, null));
                    break;
                }
                else if (Arrays.equals(m.getParameterTypes(), parameterTypes))
                {
                    ReflectConfig config = computeIfAbsent(reflectConfigs,
                        clazz.getName());
                    String[] sParameterTypes = Stream.of(parameterTypes).sequential().map(
                        Class::getName).toArray(String[]::new);
                    config.methods.add(new MethodConfig(mName, sParameterTypes));
                    break;
                }
            }
        }
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null)
        {
            addMethod(mName, parameterTypes, superClass, reflectConfigs);
        }
    }

    private static void addField(String fName, Class<?> clazz,
        List<ReflectConfig> reflectConfig)
    {
        boolean exists = Stream.of(clazz.getDeclaredFields()).anyMatch(
            f -> f.getName().equals(fName));
        if (exists)
        {
            ReflectConfig config = computeIfAbsent(reflectConfig, clazz.getName());
            config.fields.add(fName);
        }

        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null)
        {
            addField(fName, superClass, reflectConfig);
        }
    }

}
