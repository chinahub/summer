package cn.jiebaba.summer.ai.embedding;

import java.util.List;

/**
 * provider 无关的向量化模型抽象：将文本批量转为向量。
 * 用于向量库入库与检索查询向量化。
 */
public interface EmbeddingModel {

    /** 批量向量化；返回的 Embedding 列表顺序与输入一致。 */
    EmbeddingResponse embed(List<String> inputs);

    /** 单条文本向量化便捷方法。 */
    default EmbeddingResponse embed(String input) {
        return embed(List.of(input));
    }

    /** 向量维度；未知时实现可惰性探测。 */
    int dimensions();
}
