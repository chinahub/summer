package cn.jiebaba.summer.test;

import cn.jiebaba.summer.test.di.Widget;
import cn.jiebaba.summer.core.json.Json;
import cn.jiebaba.summer.core.json.TypeReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 验证 {@code Json.parse(String, Type)} / {@code Json.parse(String, TypeReference)}，
 * 这是框架此前缺失的能力（原先仅存在 parse(String, Class)）。
 */
public class JsonTypeReferenceTest {

    @Test
    void parseGenericListWithTypeReference() {
        String json = "[{\"id\":1,\"name\":\"alpha\"},{\"id\":2,\"name\":\"beta\"}]";
        List<Widget> widgets = Json.parse(json, new TypeReference<List<Widget>>() {});
        Assertions.assertEquals(2, widgets.size());
        Assertions.assertEquals("alpha", widgets.get(0).getName());
        Assertions.assertEquals("beta", widgets.get(1).getName());
        Assertions.assertEquals(2L, widgets.get(1).getId().longValue());
    }

    @Test
    void parseGenericMapWithTypeReference() {
        String json = "{\"a\":1,\"b\":2}";
        Map<String, Integer> map = Json.parse(json, new TypeReference<Map<String, Integer>>() {});
        Assertions.assertEquals(2, map.size());
        Assertions.assertEquals(1, map.get("a").intValue());
        Assertions.assertEquals(2, map.get("b").intValue());
    }

    @Test
    void parseByClassStillWorks() {
        Assertions.assertEquals("hello", Json.parse("\"hello\"", String.class));
    }
}
