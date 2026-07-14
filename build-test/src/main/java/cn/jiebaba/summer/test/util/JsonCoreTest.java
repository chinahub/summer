package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.json.Json;
import cn.jiebaba.summer.core.json.TypeReference;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

import java.util.List;
import java.util.Map;

/** 验证合并到 core 的 Json 核心能力：紧凑/美化序列化、转义、TypeReference 解析、record 往返。 */
public class JsonCoreTest {

    public record Point(int x, int y) {}

    @Test
    void stringifyCompact() {
        Assert.assertEquals("null", Json.stringify(null));
        Assert.assertEquals("42", Json.stringify(42));
        Assert.assertEquals("\"hi\"", Json.stringify("hi"));
        Assert.assertEquals("[1,2,3]", Json.stringify(new int[]{1, 2, 3}));
    }

    @Test
    void prettyHasIndentation() {
        String pretty = Json.toPretty(Map.of("a", 1));
        Assert.assertTrue(pretty.contains("\n"), pretty);
        Assert.assertTrue(pretty.contains(": "), pretty);
    }

    @Test
    void quoteAndEscape() {
        Assert.assertEquals("\"a\\nb\"", Json.quote("a\nb"));
        Assert.assertEquals("a\\nb", Json.escape("a\nb"));
    }

    @Test
    void parseWithTypeReference() {
        List<Point> pts = Json.parse("[{\"x\":1,\"y\":2},{\"x\":3,\"y\":4}]", new TypeReference<List<Point>>() {});
        Assert.assertEquals(2, pts.size());
        Assert.assertEquals(3, pts.get(1).x());
    }

    @Test
    void roundTripRecord() {
        Point p = new Point(1, 2);
        Point back = Json.parse(Json.stringify(p), Point.class);
        Assert.assertEquals(1, back.x());
        Assert.assertEquals(2, back.y());
    }
}
