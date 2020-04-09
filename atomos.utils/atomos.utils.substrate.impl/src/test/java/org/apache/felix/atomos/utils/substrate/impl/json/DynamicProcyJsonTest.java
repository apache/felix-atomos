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

import java.util.Set;

import org.apache.felix.atomos.utils.substrate.impl.config.DefaultDynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.impl.json.DynamicProxyJsonUtil;
import org.junit.jupiter.api.Test;

public class DynamicProcyJsonTest
{
    @Test
    void testJsonSimple() throws Exception
    {
        DefaultDynamicProxyConfiguration p = new DefaultDynamicProxyConfiguration();
        String json = DynamicProxyJsonUtil.json(p);
        System.out.println(json);
        json = shrinkJson(json);
        assertEquals("[]", json);
    }

    @Test
    void testJson1() throws Exception
    {
        DefaultDynamicProxyConfiguration p = new DefaultDynamicProxyConfiguration();
        p.addItem(Set.of());
        String json = DynamicProxyJsonUtil.json(p);
        System.out.println(json);
        json = shrinkJson(json);
        assertEquals("[[]]", json);
    }

    @Test
    void testJson2() throws Exception
    {
        DefaultDynamicProxyConfiguration p = new DefaultDynamicProxyConfiguration();
        p.addItem(Set.of("a"));
        String json = DynamicProxyJsonUtil.json(p);
        System.out.println(json);
        json = shrinkJson(json);
        assertEquals("[[\"a\"]]", json);
    }

    @Test
    void testJson() throws Exception
    {
        DefaultDynamicProxyConfiguration p = new DefaultDynamicProxyConfiguration();
        p.addItem(Set.of("a", "b"));
        String json = DynamicProxyJsonUtil.json(p);
        System.out.println(json);
        json = shrinkJson(json);
        assertEquals("[[\"a\",\"b\"]]", json);
    }

    @Test
    void testJson4() throws Exception
    {
        DefaultDynamicProxyConfiguration p = new DefaultDynamicProxyConfiguration();
        p.addItem(Set.of("a", "b"));
        p.addItem(Set.of("c", "d"));
        String json = DynamicProxyJsonUtil.json(p);
        System.out.println(json);
        json = shrinkJson(json);
        assertEquals("[[\"a\",\"b\"],[\"c\",\"d\"]]", json);
    }

    private String shrinkJson(String json)
    {
        json = json.replace(" ", "").replace("\n", "");
        return json;
    }

}