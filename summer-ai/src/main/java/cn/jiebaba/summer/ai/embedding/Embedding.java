package cn.jiebaba.summer.ai.embedding;

/**
 * 单条文本的向量表示：序号 index 与浮点向量 vector。
 * 序号对应批量输入中的位置，便于按顺序对齐结果。
 */
public record Embedding(int index, float[] vector) {

    /** 向量维度。 */
    public int dimensions() {
        return vector == null ? 0 : vector.length;
    }
}
