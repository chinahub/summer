package cn.jiebaba.summer.ai.structured;

import cn.jiebaba.summer.ai.chat.ChatResponse;

/**
 * 结构化响应：同时承载已转换的实体对象与原始 {@link ChatResponse}。
 * 适用于既需要解析后的强类型对象、又需要访问元数据（token 用量、finishReason 等）的场景。
 *
 * @param entity   转换后的目标实体
 * @param response 原始对话响应
 * @param <T>      目标 Java 类型
 */
public record StructuredResponse<T>(T entity, ChatResponse response) {
}
