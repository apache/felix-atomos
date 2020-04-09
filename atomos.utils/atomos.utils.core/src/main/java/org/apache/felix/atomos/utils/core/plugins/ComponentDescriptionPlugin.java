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
package org.apache.felix.atomos.utils.core.plugins;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.felix.atomos.utils.api.Config;
import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.api.plugin.ComponentDescription;
import org.apache.felix.atomos.utils.api.plugin.ComponentMetaDataPlugin;
import org.apache.felix.atomos.utils.api.plugin.ReferenceDescription;

public class ComponentDescriptionPlugin implements ComponentMetaDataPlugin<Config>
{

    @Override
    public void doComponentMetaData(ComponentDescription c, Context context,
        ClassLoader classLoader)
    {
        Class<?> clazz;
        try
        {
            clazz = classLoader.loadClass(c.implementationClass());

            // Activate Deactivate Modify
            Optional.ofNullable(c.activate()).ifPresent(
                (m) -> context.addReflectionMethod(m, clazz));
            Optional.ofNullable(c.modified()).ifPresent(
                (m) -> context.addReflectionMethod(m, clazz));
            Optional.ofNullable(c.deactivate()).ifPresent(
                (m) -> context.addReflectionMethod(m, clazz));
            if (c.activationFields() != null)
            {
                for (String fName : c.activationFields())
                {
                    context.addReflectionField(fName, clazz);
                }
            }
            //Reference
            String[] constrParams = new String[c.numberOfConstructorParameters()];
            if (c.references() != null && !c.references().isEmpty())
            {
                for (ReferenceDescription r : c.references())
                {
                    Optional.ofNullable(r.parameter()).ifPresent(
                        (i) -> constrParams[i] = r.interfaceName());
                    Optional.ofNullable(r.field()).ifPresent(
                        (f) -> context.addReflectionField(f, clazz));
                    Optional.ofNullable(r.bind()).ifPresent(
                        (m) -> context.addReflectionMethod(m, clazz));
                    Optional.ofNullable(r.updated()).ifPresent(
                        (m) -> context.addReflectionMethod(m, clazz));
                    Optional.ofNullable(r.unbind()).ifPresent(
                        (m) -> context.addReflectionMethod(m, clazz));
                    Optional.ofNullable(r.interfaceName()).ifPresent(
                        (i) -> context.addReflectionClass(i));
                }
            }
            boolean foundConstructor = false;
            if (c.numberOfConstructorParameters() == 0)
            {
                context.addReflectionConstructorDefault(clazz);

                foundConstructor = true;
            }
            else
            {
                for (Constructor<?> constructor : clazz.getConstructors())
                {
                    if (constructor.getParameterCount() != c.numberOfConstructorParameters())
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
                        context.addReflectionConstructor(clazz, ps);
                        foundConstructor = true;
                        break;
                    }
                }
                if (!foundConstructor)
                {
                    context.addReflectionConstructorAllPublic(clazz);
                }
            }

        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void init(Config config)
    {

    }
}
