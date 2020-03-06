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
package org.apache.felix.atomos.maven.reflect;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReflectConfig
{
    static Comparator<MethodConfig> mc = new Comparator<>()
    {

        @Override
        public int compare(MethodConfig o1, MethodConfig o2)
        {
            int i = o1.name.compareTo(o2.name);
            if (i == 0)
            {
                return cc.compare(o1, o2);
            }
            return i;
        }
    };

    static Comparator<ConstructorConfig> cc = new Comparator<>()
    {

        @Override
        public int compare(ConstructorConfig o1, ConstructorConfig o2)
        {
            String s1 = o1.methodParameterTypes == null ? ""
                : Stream.of(o1.methodParameterTypes).collect(Collectors.joining(","));
            String s2 = o2.methodParameterTypes == null ? ""
                : Stream.of(o2.methodParameterTypes).collect(Collectors.joining(","));

            return s1.compareTo(s2);
        }
    };
    public String className;

    public boolean allPublicConstructors = false;
    public Set<ConstructorConfig> constructor = new TreeSet<>(cc);
    public Set<String> fields = new TreeSet<>();
    public Set<MethodConfig> methods = new TreeSet<>(mc);

    public ReflectConfig(String className)
    {
        this.className = className;
    }

    @Override
    public String toString()
    {
        return "ReflectConfig [className=" + className + ", allPublicConstructors="
            + allPublicConstructors + ", constructor=" + constructor + ", fields="
            + fields + ", methods=" + methods + "]";
    }

    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof ReflectConfig))
        {
            return false;
        }
        return className == ((ReflectConfig) other).className;
    }

    @Override
    public int hashCode()
    {
        return className.hashCode();
    }

}
