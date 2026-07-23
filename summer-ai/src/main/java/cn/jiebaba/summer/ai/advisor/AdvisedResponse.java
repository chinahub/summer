package cn.jiebaba.summer.ai.advisor;

import cn.jiebaba.summer.ai.chat.ChatResponse;

import java.util.Map;

/**
 * 顾问链中的响应载体：承载模型响应与跨顾问共享的上下文（与请求同一实例）。
 *
 * @param response 模型响应
 * @param context  共享上下文
 */
public record AdvisedResponse(ChatResponse response, Map<String, Object> context) {
}
