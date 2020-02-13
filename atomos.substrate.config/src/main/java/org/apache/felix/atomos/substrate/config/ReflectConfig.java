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

import java.lang.reflect.Method;
import java.util.Dictionary;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.runtime.ServiceComponentRuntime;
import org.osgi.service.component.runtime.dto.ReferenceDTO;

@Component(service = ReflectConfig.class, property = { "osgi.command.scope=atomos",
        "osgi.command.function=reflectConfig" })
public class ReflectConfig
{
    private static final String CLASS_START = "{\n";
    private static final String CLASS_END = "}";
    private static final String COMMA = ",\n";
    private static final String CLASS_NAME = "\"name\":\"%s\"";
    private static final String FIELDS_START = "\"fields\" : [\n";
    private static final String FIELD_NAME = "{ \"name\" : \"%s\" }";
    private static final String FIELDS_END = "]";
    private static final String METHODS_START = "\"methods\" : [\n";
    private static final String METHOD_NAME = FIELD_NAME;
    private static final String METHODS_END = FIELDS_END;
    private static final String ACTIVATOR_CONSTRUCTOR = "\"methods\":[{\"name\":\"<init>\",\"parameterTypes\":[] }]";
    private static final String COMPONENT_CONSTRUCTOR = "\"allPublicConstructors\" : true";

    static class ClassConfig
    {
        final String className;
        String constructor;
        Set<String> fields = new TreeSet<>();
        Set<String> methods = new TreeSet<>();

        public ClassConfig(String className)
        {
            this.className = className;
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof ClassConfig))
            {
                return false;
            }
            return this.className == ((ClassConfig) other).className;
        }

        @Override
        public int hashCode()
        {
            return this.className.hashCode();
        }

        @Override
        public String toString()
        {
            StringBuilder builder = new StringBuilder();
            builder.append(ind(1)).append(CLASS_START);

            builder.append(ind(2)).append(String.format(CLASS_NAME, className));

            Optional.ofNullable(this.constructor).ifPresent(
                (c) -> builder.append(COMMA).append(ind(2)).append(c));

            AtomicReference<String> comma = new AtomicReference<>("");
            if (!fields.isEmpty())
            {
                builder.append(COMMA).append(ind(2)).append(FIELDS_START);
                fields.forEach(
                    f -> builder.append(comma.getAndSet(COMMA)).append(ind(3)).append(
                        String.format(FIELD_NAME, f)));
                builder.append('\n').append(ind(2)).append(FIELDS_END);
            }

            comma.set("");
            if (!methods.isEmpty())
            {
                builder.append(COMMA).append(ind(2)).append(METHODS_START);
                methods.forEach(
                    m -> builder.append(comma.getAndSet(COMMA)).append(ind(3)).append(
                        String.format(METHOD_NAME, m)));
                builder.append('\n').append(ind(2)).append(METHODS_END);
            }

            builder.append('\n').append(ind(1)).append(CLASS_END);

            return builder.toString();
        }
    }

    @Reference
    private ServiceComponentRuntime runtime;
    @Activate
    private BundleContext context;

    public void reflectConfig()
    {
        Map<String, ClassConfig> classes = new TreeMap<>();
        discoverActivators(classes);
        discoverSeriviceComponents(classes);
        printConfig(classes);
    }

    private void printConfig(Map<String, ClassConfig> classes)
    {
        StringBuilder builder = new StringBuilder();
        builder.append('[').append('\n');
        AtomicReference<String> comma = new AtomicReference<>("");
        for (ClassConfig config : classes.values())
        {
            builder.append(comma.getAndSet(COMMA)).append(config.toString());
        }
        builder.append('\n').append(']');
        System.out.println(builder.toString());
    }

    private void discoverActivators(Map<String, ClassConfig> classes)
    {
        for (Bundle b : context.getBundles())
        {
            if (b.equals(context.getBundle()))
            {
                continue;
            }
            Dictionary<String, String> headers = b.getHeaders("");
            String activator = headers.get(Constants.BUNDLE_ACTIVATOR);
            if (activator == null)
            {
                activator = headers.get(Constants.EXTENSION_BUNDLE_ACTIVATOR);
            }
            if (activator != null)
            {
                activator = activator.trim();
                ClassConfig config = classes.computeIfAbsent(activator,
                    (n) -> new ClassConfig(n));
                if (config.constructor == null)
                {
                    config.constructor = ACTIVATOR_CONSTRUCTOR;
                }
            }
        }
    }

    private void discoverSeriviceComponents(Map<String, ClassConfig> classes)
    {
        for (Bundle b : context.getBundles())
        {
            if (b.equals(context.getBundle()))
            {
                continue;
            }
            runtime.getComponentDescriptionDTOs(b).forEach((c) -> {
                Class<?> clazz;
                try
                {
                    clazz = b.loadClass(c.implementationClass);
                }
                catch (ClassNotFoundException e)
                {
                    return;
                }
                ClassConfig config = classes.computeIfAbsent(clazz.getName(),
                    (n) -> new ClassConfig(n));
                config.constructor = COMPONENT_CONSTRUCTOR;

                Optional.ofNullable(c.activate).ifPresent(
                    (m) -> addMethod(m, clazz, classes));
                Optional.ofNullable(c.modified).ifPresent(
                    (m) -> addMethod(m, clazz, classes));
                Optional.ofNullable(c.deactivate).ifPresent(
                    (m) -> addMethod(m, clazz, classes));
                for (String fName : c.activationFields)
                {
                    addField(fName, clazz, classes);
                }

                for (ReferenceDTO r : c.references)
                {
                    Optional.ofNullable(r.field).ifPresent(
                        (f) -> addField(f, clazz, classes));
                    Optional.ofNullable(r.bind).ifPresent(
                        (m) -> addMethod(m, clazz, classes));
                    Optional.ofNullable(r.updated).ifPresent(
                        (m) -> addMethod(m, clazz, classes));
                    Optional.ofNullable(r.unbind).ifPresent(
                        (m) -> addMethod(m, clazz, classes));
                    Optional.ofNullable(r.interfaceName).ifPresent(
                        (i) -> classes.computeIfAbsent(i, (n) -> new ClassConfig(n)));
                }
            });
        }
    }

    private void addMethod(String mName, Class<?> clazz, Map<String, ClassConfig> classes)
    {
        for (Method m : clazz.getDeclaredMethods())
        {
            if (mName.equals(m.getName()))
            {
                ClassConfig config = classes.computeIfAbsent(clazz.getName(),
                    (n) -> new ClassConfig(n));
                config.methods.add(mName);
                return;
            }
        }
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null)
        {
            addMethod(mName, superClass, classes);
        }
    }

    private void addField(String fName, Class<?> clazz, Map<String, ClassConfig> classes)
    {
        try
        {
            clazz.getDeclaredField(fName);
            ClassConfig config = classes.computeIfAbsent(clazz.getName(),
                (n) -> new ClassConfig(n));
            config.fields.add(fName);
        }
        catch (NoSuchFieldException e)
        {
            // ignore and move on
        }
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null)
        {
            addField(fName, superClass, classes);
        }
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
}
