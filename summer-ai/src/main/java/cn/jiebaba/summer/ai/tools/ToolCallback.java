package cn.jiebaba.summer.ai.tools;

import cn.jiebaba.summer.ai.chat.ToolDefinition;

/**
 * 工具回调抽象：提供工具定义并在模型发起调用时执行。
 * 参数与结果均为 JSON 字符串，便于跨 provider 复用与序列化。
 */
public interface ToolCallback {

    /** 工具定义：名称、说明与 JSON Schema 参数。 */
    ToolDefinition definition();

    /** 执行工具，arguments 为模型给出的 JSON 参数串，返回 JSON 结果串。 */
    String call(String arguments);
}
