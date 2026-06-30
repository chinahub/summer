package cn.jiebaba.summer.test;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.test.di.Widget;
import cn.jiebaba.summer.web.json.Json;
import cn.jiebaba.summer.web.json.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * Verifies {@code Json.parse(String, Type)} / {@code Json.parse(String, TypeReference)},
 * which the framework previously lacked (only parse(String, Class) existed).
 */
public class JsonTypeReferenceTest {

    @Test
    void parseGenericListWithTypeReference() {
        String json = "[{\"id\":1,\"name\":\"alpha\"},{\"id\":2,\"name\":\"beta\"}]";
        List<Widget> widgets = Json.parse(json, new TypeReference<List<Widget>>() {});
        Assert.assertEquals(2, widgets.size());
        Assert.assertEquals("alpha", widgets.get(0).getName());
        Assert.assertEquals("beta", widgets.get(1).getName());
        Assert.assertEquals(2L, widgets.get(1).getId().longValue());
    }

    @Test
    void parseGenericMapWithTypeReference() {
        String json = "{\"a\":1,\"b\":2}";
        Map<String, Integer> map = Json.parse(json, new TypeReference<Map<String, Integer>>() {});
        Assert.assertEquals(2, map.size());
        Assert.assertEquals(1, map.get("a").intValue());
        Assert.assertEquals(2, map.get("b").intValue());
    }

    @Test
    void parseByClassStillWorks() {
        Assert.assertEquals("hello", Json.parse("\"hello\"", String.class));
    }
}
