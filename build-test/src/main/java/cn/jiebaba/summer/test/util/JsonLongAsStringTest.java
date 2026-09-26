package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.json.Json;
import cn.jiebaba.summer.core.util.JsonUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 长整型序列化策略（{@link Json.LongAsString}）与"字符串→数字精确绑定"的单测：
 * 修复两个历史问题——① 19 位雪花 id 经 Double 中转绑定丢精度；
 * ② 超 JS 安全整数的 id 输出到浏览器被 JSON.parse 静默截断。
 */
public class JsonLongAsStringTest {

    @AfterEach
    void reset() {
        Json.setLongAsString(Json.LongAsString.AUTO);
    }

    @Test
    void autoQuotedOnlyBeyondJsSafe() {
        Assertions.assertEquals("{\"id\":\"4555555555555555555\"}",
                JsonUtil.toJsonStr(Map.of("id", 4555555555555555555L)));
        Assertions.assertEquals("{\"id\":12345}", JsonUtil.toJsonStr(Map.of("id", 12345L)));
    }

    @Test
    void alwaysQuotesAllLongs() {
        Json.setLongAsString(Json.LongAsString.ALWAYS);
        Assertions.assertEquals("{\"id\":\"12345\"}", JsonUtil.toJsonStr(Map.of("id", 12345L)));
    }

    @Test
    void neverKeepsNumbers() {
        Json.setLongAsString(Json.LongAsString.NEVER);
        Assertions.assertEquals("{\"id\":4555555555555555555}",
                JsonUtil.toJsonStr(Map.of("id", 4555555555555555555L)));
    }

    /** 回归：字符串形式数字绑定到 long/Long 字段须精确（原实现经 Double.valueOf 中转丢精度）。 */
    @Test
    void bindStringNumberToLongExactly() {
        record Holder(long id, Long opt) {}
        Holder h = JsonUtil.toBean(
                "{\"id\":\"4555555555555555555\",\"opt\":\"4555555555555555555\"}", Holder.class);
        Assertions.assertEquals(4555555555555555555L, h.id());
        Assertions.assertEquals(4555555555555555555L, h.opt().longValue());
    }

    /** 读取端兼容字符串形式数字（与字符串化输出对称）。 */
    @Test
    void getLongAcceptsStringNumber() {
        JsonUtil.JSONObject obj = JsonUtil.parseObj("{\"id\":\"4555555555555555555\"}");
        Assertions.assertEquals(4555555555555555555L, obj.getLong("id").longValue());
    }

    /** 缺键/空串返回 null（null 安全回归：曾误抛异常）。 */
    @Test
    void getLongNullSafe() {
        JsonUtil.JSONObject obj = JsonUtil.parseObj("{\"a\":\"\"}");
        Assertions.assertNull(obj.getLong("missing"));
        Assertions.assertNull(obj.getLong("a"));
    }

    /** 往返一致：AUTO 序列化为字符串后可精确解析回 Long。 */
    @Test
    void roundTripStaysExact() {
        String json = JsonUtil.toJsonStr(Map.of("id", 4555555555555555555L));
        JsonUtil.JSONObject obj = JsonUtil.parseObj(json);
        Assertions.assertEquals(4555555555555555555L, obj.getLong("id").longValue());
    }
}
