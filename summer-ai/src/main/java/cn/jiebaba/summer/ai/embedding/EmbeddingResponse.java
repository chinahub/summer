package cn.jiebaba.summer.ai.embedding;

import java.util.List;

/**
 * 向量化响应：按输入顺序排列的向量列表与实际响应模型名。
 */
public record EmbeddingResponse(List<Embedding> embeddings, String model) {
}
