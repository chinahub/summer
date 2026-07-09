package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.embedding.Embedding;
import cn.jiebaba.summer.ai.embedding.EmbeddingModel;
import cn.jiebaba.summer.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试桩 EmbeddingModel：将文本按词哈希到固定维度向量，相似词重叠越多余弦相似度越高，供向量库测试使用。
 */
public class StubEmbeddingModel implements EmbeddingModel {

    private static final int DIM = 32;

    @Override
    public EmbeddingResponse embed(List<String> inputs) {
        List<Embedding> list = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            list.add(new Embedding(i, embedOne(inputs.get(i))));
        }
        return new EmbeddingResponse(list, "stub-embedding");
    }

    @Override
    public int dimensions() {
        return DIM;
    }

    private float[] embedOne(String text) {
        float[] v = new float[DIM];
        if (text == null) {
            return v;
        }
        for (String tok : text.toLowerCase().split("\\W+")) {
            if (tok.isEmpty()) {
                continue;
            }
            v[Math.floorMod(tok.hashCode(), DIM)] += 1f;
        }
        return v;
    }
}
