package cn.jiebaba.summer.ai.chat;

/**
 * 工具定义：注入请求 tools 字段的函数描述，
 * 含名称、说明与 JSON Schema 格式的参数串。
 */
public record ToolDefinition(String name, String description, String parametersJson) {
}
