package cn.jiebaba.summer.ai.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 简单文本读取器：从字符串或 UTF-8 文本文件加载为单个 Document。
 * 文件较大时建议读取后再用 TextSplitter 切分。
 */
public class TextReader implements DocumentReader {

    private final List<Document> documents;

    public TextReader(List<Document> documents) {
        this.documents = documents;
    }

    public TextReader(Document document) {
        this.documents = List.of(document);
    }

    /** 以纯文本字符串构造，title 写入元数据。 */
    public static TextReader fromText(String text, String title) {
        return new TextReader(Document.builder().content(text).metadata("title", title).build());
    }

    /** 从 UTF-8 文本文件读取，文件名写入元数据。 */
    public static TextReader fromFile(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return new TextReader(Document.builder().content(content).metadata("source", file.toString()).build());
    }

    @Override
    public List<Document> get() {
        return documents;
    }
}
