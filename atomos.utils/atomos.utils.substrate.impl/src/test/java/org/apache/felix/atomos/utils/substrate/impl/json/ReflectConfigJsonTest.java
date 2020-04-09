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
package org.apache.felix.atomos.utils.substrate.impl.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionClassConfig;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionClassConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionConstructorConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionFieldConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultReflectionMethodConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.json.ReflectJsonUtil;
import org.junit.jupiter.api.Test;

public class ReflectConfigJsonTest
{
    @Test
    void testJsonSimple() throws Exception
    {
        DefaultReflectionClassConfiguration rc = new DefaultReflectionClassConfiguration(
            "a");
        String json = ReflectJsonUtil.json(rc);
        json = shrinkJson(json);
        assertEquals("{\"name\":\"a\"}", json);
    }

    @Test
    void testJson() throws Exception
    {
        DefaultReflectionClassConfiguration rc = new DefaultReflectionClassConfiguration(
            "a");
        rc.add(new DefaultReflectionFieldConfiguration("f1"));
        rc.add(new DefaultReflectionFieldConfiguration("f2"));

        rc.add(new DefaultReflectionConstructorConfiguration());
        rc.add(new DefaultReflectionConstructorConfiguration(new String[] { "p1" }));

        rc.add(new DefaultReflectionMethodConfiguration("m1", null));
        rc.add(new DefaultReflectionMethodConfiguration("m2", new String[] { "m2p1" }));
        rc.add(new DefaultReflectionMethodConfiguration("m3",
            new String[] { "m3p1", "m3p2" }));

        String json = ReflectJsonUtil.json(rc);
        json = shrinkJson(json);
        String exp = "{\"name\":\"a\",\"fields\":[{\"name\":\"f1\"},{\"name\":\"f2\"}],\"methods\":[{\"name\":\"<init>\",\"parameterTypes\":[]},{\"name\":\"<init>\",\"parameterTypes\":[\"p1\"]},{\"name\":\"m1\"},{\"name\":\"m2\",\"parameterTypes\":[\"m2p1\"]},{\"name\":\"m3\",\"parameterTypes\":[\"m3p1\",\"m3p2\"]}]}";
        assertEquals(exp, json);
    }

    private String shrinkJson(String json)
    {
        json = json.replace(" ", "").replace("\n", "");
        return json;
    }

    @Test
    void testJsonFull() throws Exception
    {

        List<ReflectionClassConfig> rcs = new ArrayList<>();
        rcs.add(new DefaultReflectionClassConfiguration("z"));
        rcs.add(new DefaultReflectionClassConfiguration("a"));
        String json = ReflectJsonUtil.json(rcs);
        json = shrinkJson(json);
        assertEquals("[{\"name\":\"a\"},{\"name\":\"z\"}]", json);

        ReflectionConfiguration rc = new DefaultReflectionConfiguration(rcs);
        json = ReflectJsonUtil.json(rc);
        json = shrinkJson(json);
        assertEquals("[{\"name\":\"a\"},{\"name\":\"z\"}]", json);

    }

}