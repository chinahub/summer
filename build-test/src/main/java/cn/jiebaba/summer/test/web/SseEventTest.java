package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.web.sse.SseEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/** SSE 帧编码测试：字段行、data 多行拆分、空 data 与可选字段省略。 */
public class SseEventTest {

    private static String encode(SseEvent event) {
        return new String(event.encode(), StandardCharsets.UTF_8);
    }

    @Test
    public void dataOnlyFrame() {
        Assertions.assertEquals("data: hello\n\n", encode(SseEvent.of("hello")));
    }

    @Test
    public void fullFieldsFrame() {
        String frame = encode(new SseEvent("payload", "done", "42", 3000L));
        Assertions.assertEquals("event: done\nid: 42\nretry: 3000\ndata: payload\n\n", frame);
    }

    @Test
    public void multilineDataSplitsIntoMultipleDataLines() {
        String frame = encode(SseEvent.of("line1\nline2\r\nline3"));
        Assertions.assertEquals("data: line1\ndata: line2\ndata: line3\n\n", frame);
    }

    @Test
    public void blankOptionalFieldsOmitted() {
        String frame = encode(new SseEvent("d", " ", "", null));
        Assertions.assertEquals("data: d\n\n", frame);
    }

    @Test
    public void emptyDataProducesEmptyDataLine() {
        Assertions.assertEquals("data: \n\n", encode(SseEvent.of("")));
    }
}
