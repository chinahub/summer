package cn.jiebaba.summer.ai.chat;

/**
 * 工具调用：模型返回的调用请求或历史回放，
 * 含调用 id、函数名与 JSON 格式的参数串。
 *
 * <p>流式场景下 {@link #index()} 为 OpenAI 协议中该工具调用片段在增量数组里的下标，
 * 用于跨 SSE 帧累积同一工具调用的参数片段（多个并行工具调用时区分归属）；
 * 非流式响应与历史回放场景该值为 -1（不适用）。
 */
public record ToolCall(String id, String name, String arguments, int index) {

    /** 兼容构造：未指定流式 index（非流式响应或历史回放），index 默认 -1。 */
    public ToolCall(String id, String name, String arguments) {
        this(id, name, arguments, -1);
    }
}
