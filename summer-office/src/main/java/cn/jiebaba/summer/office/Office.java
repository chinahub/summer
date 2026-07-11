package cn.jiebaba.summer.office;

import cn.jiebaba.summer.office.csv.CsvReader;
import cn.jiebaba.summer.office.csv.CsvWriter;
import cn.jiebaba.summer.office.docx.DocxReader;
import cn.jiebaba.summer.office.docx.DocxWriter;
import cn.jiebaba.summer.office.excel.ExcelReader;
import cn.jiebaba.summer.office.excel.ExcelWriter;
import cn.jiebaba.summer.office.md.MarkdownReader;
import cn.jiebaba.summer.office.md.MarkdownWriter;
import cn.jiebaba.summer.office.pdf.PdfReader;
import cn.jiebaba.summer.office.pdf.PdfWriter;
import cn.jiebaba.summer.office.xml.XmlReader;
import cn.jiebaba.summer.office.xml.XmlWriter;

/**
 * Office 门面：统一入口，按格式创建对应的读取器与写入器。
 * <p>用法一（依赖注入）：由 {@code OfficeAutoConfiguration} 注册为 Bean，直接注入 {@code Office}；
 * 用法二（直接使用）：{@code Office.create().csvReader()} 静态工厂创建实例。
 * <p>纯 JDK 格式（全部零第三方依赖）：
 * <ul>
 *   <li>CSV - {@link CsvReader}/{@link CsvWriter}（RFC 4180 兼容）</li>
 *   <li>Markdown - {@link MarkdownReader}/{@link MarkdownWriter}</li>
 *   <li>XML - {@link XmlReader}/{@link XmlWriter}（StAX 流式）</li>
 * </ul>
 * 以下格式同样纯 JDK 实现（零第三方依赖）：
 * <ul>
 *   <li>XLSX - {@link ExcelReader}/{@link ExcelWriter}（纯 JDK SAX 流式，读取前按魔数校验仅支持 XLSX）</li>
 *   <li>DOCX - {@link DocxReader}/{@link DocxWriter}（纯 JDK，ZipFile + StAX 流式）</li>
 *   <li>PDF - {@link PdfReader}/{@link PdfWriter}（纯 JDK，直接生成/解析 PDF 对象结构）</li>
 * </ul>
 * 详见开发路线图 office 章节。
 */
public final class Office {

    private Office() {
    }

    /** 创建 Office 实例，用于直接调用各格式工厂方法。 */
    public static Office create() {
        return new Office();
    }

    // ==================== 纯 JDK 格式 ====================

    /** 创建默认 CSV 读取器（逗号分隔，含表头）。 */
    public TableReader csvReader() {
        return new CsvReader();
    }

    /** 创建默认 CSV 写入器（逗号分隔，无 BOM）。 */
    public TableWriter csvWriter() {
        return new CsvWriter();
    }

    /** 创建 Markdown 读取器。 */
    public OfficeReader markdownReader() {
        return new MarkdownReader();
    }

    /** 创建 Markdown 写入器。 */
    public OfficeWriter markdownWriter() {
        return new MarkdownWriter();
    }

    /** 创建 XML 读取器（StAX，提取元素文本）。 */
    public OfficeReader xmlReader() {
        return new XmlReader();
    }

    /** 创建 XML 写入器（StAX，默认 document/content 结构）。 */
    public OfficeWriter xmlWriter() {
        return new XmlWriter();
    }

    // ==================== 第三方库格式 ====================

    /** 创建 Excel 读取器（纯 JDK SAX 流式，校验仅支持 XLSX）。 */
    public TableReader excelReader() {
        return new ExcelReader();
    }

    /** 创建 Excel 写入器（纯 JDK ZipOutputStream 流式，仅输出 XLSX）。 */
    public TableWriter excelWriter() {
        return new ExcelWriter();
    }

    /** 创建 DOCX 读取器（纯 JDK，ZipFile + StAX 解析 word/document.xml）。 */
    public OfficeReader docxReader() {
        return new DocxReader();
    }

    /** 创建 DOCX 写入器（纯 JDK，ZipOutputStream 生成最小 DOCX 结构）。 */
    public OfficeWriter docxWriter() {
        return new DocxWriter();
    }

    /** 创建 PDF 读取器（纯 JDK，流扫描 + zlib 解压 + 文本操作符解析）。 */
    public OfficeReader pdfReader() {
        return new PdfReader();
    }

    /** 创建 PDF 写入器（纯 JDK，直接生成 PDF 对象结构）。 */
    public OfficeWriter pdfWriter() {
        return new PdfWriter();
    }
}
