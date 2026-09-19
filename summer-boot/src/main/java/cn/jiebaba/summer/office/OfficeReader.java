package cn.jiebaba.summer.office;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档读取抽象：将输入流解析为文本内容。
 * <p>适用于 Markdown（原文）、XML（元素文本内容提取）、PDF/DOCX（文本提取）等格式。
 * 表格类格式（CSV/Excel）请使用 {@link TableReader}。
 */
public interface OfficeReader {

    /** 从输入流读取并解析为文本；调用方负责关闭输入流。 */
    String read(InputStream in) throws IOException;
}
