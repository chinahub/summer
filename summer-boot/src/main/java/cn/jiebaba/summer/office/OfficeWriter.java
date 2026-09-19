package cn.jiebaba.summer.office;

import java.io.IOException;

/**
 * 文档生成抽象：将文本内容生成为目标格式的字节数组。
 * <p>适用于 Markdown（原样写出）、XML（包装为文档结构）、PDF/DOCX（文本排版生成）等格式。
 * 表格类格式（CSV/Excel）请使用 {@link TableWriter}。
 */
public interface OfficeWriter {

    /** 将文本内容生成为目标格式的字节；返回的字节可直接写入文件或响应体。 */
    byte[] write(String content) throws IOException;
}
