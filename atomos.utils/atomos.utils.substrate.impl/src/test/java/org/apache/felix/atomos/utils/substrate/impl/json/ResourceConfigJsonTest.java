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

import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.config.DefaultResourceConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.json.ResourceJsonUtil;
import org.junit.jupiter.api.Test;

public class ResourceConfigJsonTest
{
    @Test
    void testJsonEmpty() throws Exception
    {
        ResourceConfiguration c = new DefaultResourceConfiguration();
        String json = ResourceJsonUtil.json(c);
        json = shrinkJson(json);
        assertEquals("{}", json);
    }

    @Test
    void testJsonResourceBundle1() throws Exception
    {
        DefaultResourceConfiguration c = new DefaultResourceConfiguration();
        c.addResourceBundle("resourceBundle1");
        String json = ResourceJsonUtil.json(c);
        json = shrinkJson(json);
        assertEquals("{\"bundles\":[{\"name\":\"resourceBundle1\"}]}", json);
    }

    @Test
    void testJsonResourceBundle2() throws Exception
    {
        DefaultResourceConfiguration c = new DefaultResourceConfiguration();
        c.addResourceBundle("resourceBundle2");
        c.addResourceBundle("resourceBundle1");
        String json = ResourceJsonUtil.json(c);
        json = shrinkJson(json);
        assertEquals(
            "{\"bundles\":[{\"name\":\"resourceBundle1\"},{\"name\":\"resourceBundle2\"}]}",
            json);
    }

    @Test
    void testJsonResourcePattern1() throws Exception
    {
        DefaultResourceConfiguration c = new DefaultResourceConfiguration();
        c.addResourcePattern("resourcePattern1");
        String json = ResourceJsonUtil.json(c);
        json = shrinkJson(json);
        assertEquals("{\"resources\":[{\"pattern\":\"resourcePattern1\"}]}", json);
    }

    @Test
    void testJsonResourcePattern2() throws Exception
    {
        DefaultResourceConfiguration c = new DefaultResourceConfiguration();

        c.addResourcePattern("resourcePattern2");
        c.addResourcePattern("resourcePattern1");
        String json = ResourceJsonUtil.json(c);
        json = shrinkJson(json);
        assertEquals(
            "{\"resources\":[{\"pattern\":\"resourcePattern1\"},{\"pattern\":\"resourcePattern2\"}]}",
            json);
    }

    @Test
    void testJsonFull() throws Exception
    {
        DefaultResourceConfiguration c = new DefaultResourceConfiguration();
        c.addResourceBundle("resourceBundle2");
        c.addResourceBundle("resourceBundle1");

        c.addResourcePattern("resourcePattern2");
        c.addResourcePattern("resourcePattern1");
        String json = ResourceJsonUtil.json(c);
        json = shrinkJson(json);
        assertEquals(
            "{\"bundles\":[{\"name\":\"resourceBundle1\"},{\"name\":\"resourceBundle2\"}],\"resources\":[{\"pattern\":\"resourcePattern1\"},{\"pattern\":\"resourcePattern2\"}]}",
            json);
    }

    private String shrinkJson(String json)
    {
        json = json.replace(" ", "").replace("\n", "");
        return json;
    }

}