package cn.jiebaba.summer.office.md;

import cn.jiebaba.summer.office.OfficeReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Markdown 读取器：纯 JDK 实现，将 .md 文件读取为原始文本。
 * <p>Markdown 本质为纯文本，读取即为 UTF-8 解码；不做 HTML 渲染，
 * 若需 Markdown->HTML 转换可后续引入 commonmark-java（Apache-2.0）。
 */
public class MarkdownReader implements OfficeReader {

    @Override
    public String read(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
