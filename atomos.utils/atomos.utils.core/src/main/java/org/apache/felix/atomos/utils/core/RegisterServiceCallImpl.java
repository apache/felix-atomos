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

import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.utils.api.RegisterServiceCall;

public class RegisterServiceCallImpl implements RegisterServiceCall
{

    private Map<String, ?> cfg = null;
    private String[] classes;
    private final Object service;
    private boolean valid = true;

    public RegisterServiceCallImpl(Object[] args)
    {
        service = args[1];

        if (args[2] != null)
        {
            Dictionary<String, ?> dict = (Dictionary<String, ?>) args[2];

            cfg = Collections.list(dict.keys()).stream().collect(
                Collectors.toMap(Function.identity(), dict::get));
        }
        else
        {
            cfg = new HashMap<>();
        }

        if (args[0] instanceof Class)
        {
            classes = new String[] { ((Class<?>) args[0]).getName() };
        }
        else if (args[0] instanceof Class[])
        {
            classes = Stream.of((Class[]) args[0]).map(Class::getName).toArray(
                String[]::new);
        }
        else if (args[0] instanceof String)
        {
            classes = new String[] { ((String) args[0]) };
        }
        else if (args[0] instanceof String[])
        {
            classes = ((String[]) args[0]);
        }
        else
        {
            valid = false;
        }
    }

    @Override
    public String[] classes()
    {
        return classes;
    }

    @Override
    public Map<String, ?> config()
    {
        return cfg;
    }

    public boolean isValid()
    {
        return valid;
    }

    @Override
    public Object service()
    {
        return service;
    }

}
