package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.core.util.JsonUtil;
import cn.jiebaba.summer.core.util.JsonUtil.JSONArray;
import cn.jiebaba.summer.core.util.JsonUtil.JSONObject;

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
        Assert.assertEquals("null", JsonUtil.toJsonStr(null));
        Assert.assertEquals("true", JsonUtil.toJsonStr(true));
        Assert.assertEquals("42", JsonUtil.toJsonStr(42));
        Assert.assertEquals("\"hi\"", JsonUtil.toJsonStr("hi"));
        Assert.assertEquals("[1,2,3]", JsonUtil.toJsonStr(new int[]{1, 2, 3}));
        Assert.assertEquals("[1,2,3]", JsonUtil.toJsonStr(List.of(1, 2, 3)));
        java.util.Map<String, Integer> om = new java.util.LinkedHashMap<>(); om.put("a", 1); om.put("b", 2);
        Assert.assertEquals("{\"a\":1,\"b\":2}", JsonUtil.toJsonStr(om));
    }

    @Test
    public void escapeAndQuote() {
        Assert.assertEquals("\"a\\nb\"", JsonUtil.quote("a\nb"));
        Assert.assertEquals("a\\nb", JsonUtil.escape("a\nb"));
        Assert.assertTrue(JsonUtil.toJsonStr("a\"b").contains("\\\""));
    }

    @Test
    public void recordRoundTrip() {
        Person p = new Person("Alice", 30, true);
        String json = JsonUtil.toJsonStr(p);
        Assert.assertTrue(json.contains("\"name\":\"Alice\""));
        Assert.assertTrue(json.contains("\"age\":30"));
        Person back = JsonUtil.toBean(json, Person.class);
        Assert.assertEquals("Alice", back.name());
        Assert.assertEquals(30, back.age());
        Assert.assertTrue(back.active());
    }

    @Test
    public void beanRoundTrip() {
        Box box = new Box();
        box.label = "gift";
        box.count = 7;
        Box back = JsonUtil.toBean(JsonUtil.toJsonStr(box), Box.class);
        Assert.assertEquals("gift", back.label);
        Assert.assertEquals(7, back.count);
    }

    @Test
    public void parseObjAndArray() {
        JSONObject obj = JsonUtil.parseObj("{\"name\":\"Bob\",\"age\":25,\"nested\":{\"k\":\"v\"}}");
        Assert.assertEquals("Bob", obj.getStr("name"));
        Assert.assertEquals(25, obj.getInt("age").intValue());
        Assert.assertEquals("v", obj.getJSONObject("nested").getStr("k"));

        JSONArray arr = JsonUtil.parseArray("[1,2,3]");
        Assert.assertEquals(3, arr.size());
        Assert.assertEquals(2, arr.getInt(1).intValue());
    }

    @Test
    public void toList() {
        List<Person> people = JsonUtil.toList("[{\"name\":\"A\",\"age\":1,\"active\":false},{\"name\":\"B\",\"age\":2,\"active\":true}]", Person.class);
        Assert.assertEquals(2, people.size());
        Assert.assertEquals("B", people.get(1).name());
        Assert.assertEquals(2, people.get(1).age());
    }

    @Test
    public void nestedBeanViaAccessor() {
        JSONObject obj = JsonUtil.parseObj("{\"label\":\"x\",\"count\":3}");
        Box box = obj.getBean("notthere", Box.class);
        Assert.assertNull(box);
        Box direct = JsonUtil.toBean(JsonUtil.toJsonStr(obj), Box.class);
        Assert.assertEquals("x", direct.label);
        Assert.assertEquals(3, direct.count);
    }

    @Test
    public void prettyHasNewlines() {
        String pretty = JsonUtil.toJsonPrettyStr(Map.of("a", 1));
        Assert.assertTrue(pretty.contains("\n"));
        Assert.assertTrue(pretty.contains(": "));
    }

    @Test
    public void isJson() {
        Assert.assertTrue(JsonUtil.isJson("{\"a\":1}"));
        Assert.assertTrue(JsonUtil.isJson("[1,2]"));
        Assert.assertFalse(JsonUtil.isJson("not json"));
        Assert.assertFalse(JsonUtil.isJson(null));
    }

    @Test
    public void enumAndOptional() {
        Assert.assertEquals("\"MONDAY\"", JsonUtil.toJsonStr(java.time.DayOfWeek.MONDAY));
        Assert.assertEquals("30", JsonUtil.toJsonStr(java.util.Optional.of(30)));
        Assert.assertEquals("null", JsonUtil.toJsonStr(java.util.Optional.empty()));
    }

    @Test
    public void mapWithGenericValues() {
        Map<String, Integer> m = JsonUtil.toBean("{\"a\":1,\"b\":2}", new java.lang.reflect.ParameterizedType() {
            public java.lang.reflect.Type[] getActualTypeArguments() { return new java.lang.reflect.Type[]{String.class, Integer.class}; }
            public java.lang.reflect.Type getRawType() { return Map.class; }
            public java.lang.reflect.Type getOwnerType() { return null; }
        });
        Assert.assertEquals(1, m.get("a").intValue());
        Assert.assertEquals(2, m.get("b").intValue());
    }
}