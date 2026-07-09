package cn.jiebaba.summer.ai.chat;

/**
 * 模型响应元数据：模型名、token 用量与缓存命中。
 * promptCacheHitTokens 为 DeepSeek 等厂商的缓存命中 token 数，未提供时为 null。
 */
public record ChatResponseMetadata(
        String model,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        Long promptCacheHitTokens) {
}
