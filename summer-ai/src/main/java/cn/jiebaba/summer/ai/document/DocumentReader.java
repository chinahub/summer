package cn.jiebaba.summer.ai.document;

import java.util.List;

/**
 * 文档读取抽象：从某种来源（文件、字符串、网页等）加载为 Document 列表。
 * 与切分器 TextSplitter 解耦，读取得到原始文档后再按需分块。
 */
public interface DocumentReader {

    List<Document> get();
}
