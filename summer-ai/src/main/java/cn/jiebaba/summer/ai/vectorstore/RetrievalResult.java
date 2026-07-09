package cn.jiebaba.summer.ai.vectorstore;

import cn.jiebaba.summer.ai.document.Document;

/**
 * 检索单条结果：命中文档与其相似度得分（越高越相关）。
 */
public record RetrievalResult(Document document, double score) {
}
