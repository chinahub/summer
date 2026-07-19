package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.json.Json;
import cn.jiebaba.summer.core.json.TypeReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 验证合并到 core 的 Json 核心能力：紧凑/美化序列化、转义、TypeReference 解析、record 往返。 */
public class JsonCoreTest {

    public record Point(int x, int y) {}

    @Test
    void stringifyCompact() {
        Assertions.assertEquals("null", Json.stringify(null));
        Assertions.assertEquals("42", Json.stringify(42));
        Assertions.assertEquals("\"hi\"", Json.stringify("hi"));
        Assertions.assertEquals("[1,2,3]", Json.stringify(new int[]{1, 2, 3}));
    }

    @Test
    void prettyHasIndentation() {
        String pretty = Json.toPretty(Map.of("a", 1));
        Assertions.assertTrue(pretty.contains("\n"), pretty);
        Assertions.assertTrue(pretty.contains(": "), pretty);
    }

    @Test
    void quoteAndEscape() {
        Assertions.assertEquals("\"a\\nb\"", Json.quote("a\nb"));
        Assertions.assertEquals("a\\nb", Json.escape("a\nb"));
    }

    @Test
    void parseWithTypeReference() {
        List<Point> pts = Json.parse("[{\"x\":1,\"y\":2},{\"x\":3,\"y\":4}]", new TypeReference<List<Point>>() {});
        Assertions.assertEquals(2, pts.size());
        Assertions.assertEquals(3, pts.get(1).x());
    }

    @Test
    void roundTripRecord() {
        Point p = new Point(1, 2);
        Point back = Json.parse(Json.stringify(p), Point.class);
        Assertions.assertEquals(1, back.x());
        Assertions.assertEquals(2, back.y());
    }
}
