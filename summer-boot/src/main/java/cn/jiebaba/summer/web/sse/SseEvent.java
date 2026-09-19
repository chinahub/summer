package cn.jiebaba.summer.web.sse;

import java.nio.charset.StandardCharsets;

/**
 * SSE（Server-Sent Events）事件帧：data 必备，event/id/retry 可选。
 * {@link #encode()} 按 W3C SSE 规范编码为帧文本（各字段行 + 空行结尾；
 * data 中的换行拆分为多个 {@code data:} 行）。配合 {@link SseEmitter#send(Object)}
 * 或直接作为 handler 返回 {@code Stream<SseEvent>} 的元素使用。
 */
public record SseEvent(String data, String event, String id, Long retry) {

    /** 仅含 data 的便捷事件。 */
    public static SseEvent of(String data) {
        return new SseEvent(data, null, null, null);
    }

    /** 指定事件名与 data 的便捷事件。 */
    public static SseEvent of(String data, String event) {
        return new SseEvent(data, event, null, null);
    }

    /** 按 W3C SSE 规范编码：字段行 + data 行 + 终止空行，UTF-8 字节。 */
    public byte[] encode() {
        StringBuilder sb = new StringBuilder(64);
        if (event != null && !event.isBlank()) sb.append("event: ").append(event).append('\n');
        if (id != null && !id.isBlank()) sb.append("id: ").append(id).append('\n');
        if (retry != null) sb.append("retry: ").append(retry).append('\n');
        String payload = data == null ? "" : data;
        for (String line : payload.split("\r?\n", -1)) {
            sb.append("data: ").append(line).append('\n');
        }
        sb.append('\n');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
