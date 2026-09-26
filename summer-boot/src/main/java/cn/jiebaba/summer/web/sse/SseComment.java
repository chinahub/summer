package cn.jiebaba.summer.web.sse;

import java.nio.charset.StandardCharsets;

/**
 * SSE 注释帧（{@code : text} + 空行）：不派发给 EventSource 的任何事件处理器，
 * 常用于心跳保活（防止代理/浏览器空闲断连）或调试标注。
 * 可经 {@link SseEmitter#send(Object)} 推送，或由 {@link SseHub} 心跳自动发送。
 */
public record SseComment(String text) {

    /** 便捷构造：无文本注释（纯心跳帧）。 */
    public static SseComment heartbeat() {
        return new SseComment("hb");
    }

    /** 按 W3C SSE 规范编码注释帧。 */
    public byte[] encode() {
        return (": " + (text == null ? "" : text) + "\n\n").getBytes(StandardCharsets.UTF_8);
    }
}
