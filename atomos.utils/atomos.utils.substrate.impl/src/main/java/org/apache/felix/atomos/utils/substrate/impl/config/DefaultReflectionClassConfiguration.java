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
package org.apache.felix.atomos.utils.substrate.impl.config;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionClassConfig;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConstructorConfig;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionFieldConfig;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionMethodConfig;

public class DefaultReflectionClassConfiguration implements ReflectionClassConfig
{
    public static Comparator<ReflectionConstructorConfig> cc = (o1, o2) -> {
        final String s1 = o1.getMethodParameterTypes() == null ? ""
            : Stream.of(o1.getMethodParameterTypes()).collect(Collectors.joining(","));
        final String s2 = o2.getMethodParameterTypes() == null ? ""
            : Stream.of(o2.getMethodParameterTypes()).collect(Collectors.joining(","));

        return s1.compareTo(s2);
    };

    public static Comparator<ReflectionFieldConfig> fc = (o1,
        o2) -> o1.getFieldName().compareTo(o2.getFieldName());

        public static Comparator<ReflectionMethodConfig> mc = (o1, o2) -> {
            final int i = o1.getName().compareTo(o2.getName());
            if (i == 0)
            {
                return cc.compare(o1, o2);
            }
            return i;
        };
        private boolean allPublicConstructors = false;

        private boolean allPublicFields = false;
        private boolean allPublicMethods = false;
        String className;

        private final Set<DefaultReflectionConstructorConfiguration> constructor = new TreeSet<>(cc);
        private final Set<DefaultReflectionFieldConfiguration> fields = new TreeSet<>(fc);
        private final Set<DefaultReflectionMethodConfiguration> methods = new TreeSet<>(mc);

        private DefaultReflectionClassConfiguration()
        {
        }

        public DefaultReflectionClassConfiguration(final String className)
        {
            this();
            this.className = className;
        }

        public void add(final DefaultReflectionConstructorConfiguration constructorConfigImpl)
        {
            constructor.add(constructorConfigImpl);
        }

        public void add(final DefaultReflectionFieldConfiguration fieldConfigImpl)
        {
            fields.add(fieldConfigImpl);
        }

        public void add(final DefaultReflectionMethodConfiguration methodConfigImpl)
        {
            methods.add(methodConfigImpl);
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((className == null) ? 0 : className.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            DefaultReflectionClassConfiguration other = (DefaultReflectionClassConfiguration) obj;
            if (className == null)
            {
                if (other.className != null)
                {
                    return false;
                }
            }
            else if (!className.equals(other.className))
            {
                return false;
            }
            return true;
        }

        @Override
        public String getClassName()
        {
            return className;
        }

        @Override
        public Set<ReflectionConstructorConfig> getConstructors()
        {
            return Set.copyOf(constructor);
        }

        @Override
        public Set<ReflectionFieldConfig> getFields()
        {
            return Set.copyOf(fields);
        }

        @Override
        public Set<ReflectionMethodConfig> getMethods()
        {

            return Set.copyOf(methods);
        }

        @Override
        public boolean isAllPublicConstructors()
        {
            return allPublicConstructors;
        }

        @Override
        public boolean isAllPublicFields()
        {
            return allPublicFields;
        }

        @Override
        public boolean isAllPublicMethods()
        {
            return allPublicMethods;
        }

        public void setAllPublicConstructors(final boolean b)
        {

            allPublicConstructors = b;
        }

        public void setAllPublicFields(final boolean b)
        {
            allPublicFields = b;
        }


        public void setAllPublicMethods(final boolean b)
        {
            allPublicMethods = b;
        }

}
