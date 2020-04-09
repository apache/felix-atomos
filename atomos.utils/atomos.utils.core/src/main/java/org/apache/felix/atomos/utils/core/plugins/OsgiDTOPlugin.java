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

import java.lang.reflect.Field;

import org.apache.felix.atomos.utils.api.Config;
import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.api.plugin.ClassPlugin;

public class OsgiDTOPlugin implements ClassPlugin<Config>
{

    @Override
    public void doClass(Class<?> clazz, Context context)
    {
        boolean isDTO = false;
        Class<?> c = clazz;
        while (c != null && c != Object.class)
        {
            if ("org.osgi.dto.DTO".equals(c.getName()))
            {
                isDTO = true;
                break;
            }
            c = c.getSuperclass();
        }

        if (isDTO)
        {
            for (Field field : clazz.getFields())
            {
                context.addReflectionField(field.getName(), clazz);
            }
        }
    }

    @Override
    public void init(Config config)
    {

    }

}
