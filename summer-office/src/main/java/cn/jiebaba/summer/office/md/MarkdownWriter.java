package cn.jiebaba.summer.office.md;

import cn.jiebaba.summer.office.OfficeWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Markdown 写入器：纯 JDK 实现，将文本内容以 UTF-8 编码写出。
 * <p>Markdown 本质为纯文本，写入即为 UTF-8 编码；提供标题与列表的便捷构造方法。
 */
public class MarkdownWriter implements OfficeWriter {

    private final boolean trailingNewline;

    public MarkdownWriter() {
        this(true);
    }

    /** 指定是否在末尾追加换行符。 */
    public MarkdownWriter(boolean trailingNewline) {
        this.trailingNewline = trailingNewline;
    }

    @Override
    public byte[] write(String content) throws IOException {
        String text = content == null ? "" : content;
        if (trailingNewline && !text.endsWith("\n")) {
            text = text + "\n";
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** 构造一级标题字节。 */
    public byte[] heading(int level, String text) {
        String prefix = "#".repeat(Math.max(1, level));
        return (prefix + " " + text + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
