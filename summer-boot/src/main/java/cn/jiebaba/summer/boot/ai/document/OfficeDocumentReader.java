package cn.jiebaba.summer.boot.ai.document;

import cn.jiebaba.summer.ai.document.Document;
import cn.jiebaba.summer.ai.document.DocumentReader;
import cn.jiebaba.summer.office.OfficeReader;
import cn.jiebaba.summer.office.docx.DocxReader;
import cn.jiebaba.summer.office.md.MarkdownReader;
import cn.jiebaba.summer.office.pdf.PdfReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 以 summer-office 的 OfficeReader 为底座实现的 summer-ai DocumentReader 适配器：
 * 将 PDF/DOCX/Markdown 文件解析为纯文本并装入 Document，供后续切分与向量化入库。
 * <p>该类位于 summer-boot（同时编译期依赖 summer-ai 与 summer-office，均为 optional），
 * 是两个纯模块之间的衔接层；仅在显式构造时加载，未使用时不影响应用。
 * <p>读取仅产出单个原文 Document，文本切分交由 {@link cn.jiebaba.summer.ai.document.TextSplitter} 完成，
 * 与 {@link cn.jiebaba.summer.ai.document.TextReader} 的职责划分保持一致。
 */
public class OfficeDocumentReader implements DocumentReader {

    private final OfficeReader delegate;
    private final byte[] content;
    private final String source;

    /** 以指定 OfficeReader 解析给定字节内容，source 写入元数据标识来源。 */
    public OfficeDocumentReader(OfficeReader delegate, byte[] content, String source) {
        this.delegate = delegate;
        this.content = content == null ? new byte[0] : content;
        this.source = source == null ? "" : source;
    }

    /** 调用底层 OfficeReader 提取文本，封装为带 source 元数据的单个 Document 返回。 */
    @Override
    public List<Document> get() {
        String text;
        try {
            text = delegate.read(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new IllegalStateException("文档读取失败: " + source, e);
        }
        return List.of(Document.builder().content(text).metadata("source", source).build());
    }

    /** 读取 Markdown 文件（原文直接作为文本，保留格式）。 */
    public static OfficeDocumentReader markdown(Path file) throws IOException {
        return new OfficeDocumentReader(new MarkdownReader(), Files.readAllBytes(file), file.toString());
    }

    /** 读取 Markdown 字节内容，source 标识来源。 */
    public static OfficeDocumentReader markdown(byte[] content, String source) {
        return new OfficeDocumentReader(new MarkdownReader(), content, source);
    }

    /** 读取 PDF 文件并提取文本（纯 JDK 解析，不依赖 PDFBox 等第三方库）。 */
    public static OfficeDocumentReader pdf(Path file) throws IOException {
        return new OfficeDocumentReader(new PdfReader(), Files.readAllBytes(file), file.toString());
    }

    /** 读取 PDF 字节内容并提取文本，source 标识来源。 */
    public static OfficeDocumentReader pdf(byte[] content, String source) {
        return new OfficeDocumentReader(new PdfReader(), content, source);
    }

    /** 读取 DOCX 文件并提取文本（解析 word/document.xml 的段落与表格文本）。 */
    public static OfficeDocumentReader docx(Path file) throws IOException {
        return new OfficeDocumentReader(new DocxReader(), Files.readAllBytes(file), file.toString());
    }

    /** 读取 DOCX 字节内容并提取文本，source 标识来源。 */
    public static OfficeDocumentReader docx(byte[] content, String source) {
        return new OfficeDocumentReader(new DocxReader(), content, source);
    }
}
