package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.core.util.JsonUtil.JSONArray;
import cn.jiebaba.summer.core.util.JsonUtil.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class JsonUtilTest {

    public record Person(String name, int age, boolean active) {}

    public static class Box {
        public String label;
        public int count;
    }

    @Test
    public void stringifyPrimitives() {
        Assertions.assertEquals("null", JsonUtil.toJsonStr(null));
        Assertions.assertEquals("true", JsonUtil.toJsonStr(true));
        Assertions.assertEquals("42", JsonUtil.toJsonStr(42));
        Assertions.assertEquals("\"hi\"", JsonUtil.toJsonStr("hi"));
        Assertions.assertEquals("[1,2,3]", JsonUtil.toJsonStr(new int[]{1, 2, 3}));
        Assertions.assertEquals("[1,2,3]", JsonUtil.toJsonStr(List.of(1, 2, 3)));
        java.util.Map<String, Integer> om = new java.util.LinkedHashMap<>(); om.put("a", 1); om.put("b", 2);
        Assertions.assertEquals("{\"a\":1,\"b\":2}", JsonUtil.toJsonStr(om));
    }

    @Test
    public void escapeAndQuote() {
        Assertions.assertEquals("\"a\\nb\"", JsonUtil.quote("a\nb"));
        Assertions.assertEquals("a\\nb", JsonUtil.escape("a\nb"));
        Assertions.assertTrue(JsonUtil.toJsonStr("a\"b").contains("\\\""));
    }

    @Test
    public void recordRoundTrip() {
        Person p = new Person("Alice", 30, true);
        String json = JsonUtil.toJsonStr(p);
        Assertions.assertTrue(json.contains("\"name\":\"Alice\""));
        Assertions.assertTrue(json.contains("\"age\":30"));
        Person back = JsonUtil.toBean(json, Person.class);
        Assertions.assertEquals("Alice", back.name());
        Assertions.assertEquals(30, back.age());
        Assertions.assertTrue(back.active());
    }

    @Test
    public void beanRoundTrip() {
        Box box = new Box();
        box.label = "gift";
        box.count = 7;
        Box back = JsonUtil.toBean(JsonUtil.toJsonStr(box), Box.class);
        Assertions.assertEquals("gift", back.label);
        Assertions.assertEquals(7, back.count);
    }

    @Test
    public void parseObjAndArray() {
        JSONObject obj = JsonUtil.parseObj("{\"name\":\"Bob\",\"age\":25,\"nested\":{\"k\":\"v\"}}");
        Assertions.assertEquals("Bob", obj.getStr("name"));
        Assertions.assertEquals(25, obj.getInt("age").intValue());
        Assertions.assertEquals("v", obj.getJSONObject("nested").getStr("k"));

        JSONArray arr = JsonUtil.parseArray("[1,2,3]");
        Assertions.assertEquals(3, arr.size());
        Assertions.assertEquals(2, arr.getInt(1).intValue());
    }

    @Test
    public void toList() {
        List<Person> people = JsonUtil.toList("[{\"name\":\"A\",\"age\":1,\"active\":false},{\"name\":\"B\",\"age\":2,\"active\":true}]", Person.class);
        Assertions.assertEquals(2, people.size());
        Assertions.assertEquals("B", people.get(1).name());
        Assertions.assertEquals(2, people.get(1).age());
    }

    @Test
    public void nestedBeanViaAccessor() {
        JSONObject obj = JsonUtil.parseObj("{\"label\":\"x\",\"count\":3}");
        Box box = obj.getBean("notthere", Box.class);
        Assertions.assertNull(box);
        Box direct = JsonUtil.toBean(JsonUtil.toJsonStr(obj), Box.class);
        Assertions.assertEquals("x", direct.label);
        Assertions.assertEquals(3, direct.count);
    }

    @Test
    public void prettyHasNewlines() {
        String pretty = JsonUtil.toJsonPrettyStr(Map.of("a", 1));
        Assertions.assertTrue(pretty.contains("\n"));
        Assertions.assertTrue(pretty.contains(": "));
    }

    @Test
    public void isJson() {
        Assertions.assertTrue(JsonUtil.isJson("{\"a\":1}"));
        Assertions.assertTrue(JsonUtil.isJson("[1,2]"));
        Assertions.assertFalse(JsonUtil.isJson("not json"));
        Assertions.assertFalse(JsonUtil.isJson(null));
    }

    @Test
    public void enumAndOptional() {
        Assertions.assertEquals("\"MONDAY\"", JsonUtil.toJsonStr(java.time.DayOfWeek.MONDAY));
        Assertions.assertEquals("30", JsonUtil.toJsonStr(java.util.Optional.of(30)));
        Assertions.assertEquals("null", JsonUtil.toJsonStr(java.util.Optional.empty()));
    }

    @Test
    public void mapWithGenericValues() {
        Map<String, Integer> m = JsonUtil.toBean("{\"a\":1,\"b\":2}", new java.lang.reflect.ParameterizedType() {
            public java.lang.reflect.Type[] getActualTypeArguments() { return new java.lang.reflect.Type[]{String.class, Integer.class}; }
            public java.lang.reflect.Type getRawType() { return Map.class; }
            public java.lang.reflect.Type getOwnerType() { return null; }
        });
        Assertions.assertEquals(1, m.get("a").intValue());
        Assertions.assertEquals(2, m.get("b").intValue());
    }
}