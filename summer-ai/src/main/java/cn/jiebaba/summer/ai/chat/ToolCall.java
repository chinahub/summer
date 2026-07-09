package cn.jiebaba.summer.ai.chat;

/**
 * 工具调用：模型返回的调用请求或历史回放，
 * 含调用 id、函数名与 JSON 格式的参数串。
 */
public record ToolCall(String id, String name, String arguments) {
}
