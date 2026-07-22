# summer-office 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-office/` 目录下的文件时才会加载本文件。

## 模块职责

文档读写（Excel/Word/PDF/CSV/Markdown/XML）+ OCR。纯 JDK 实现 csv/md/xml 读写；xlsx/docx/pdf 按 classpath 探测激活（需要 POI/PDFBox 依赖）。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summe.office` | 核心抽象：Office/OfficeReader/OfficeWriter/TableReader/TableWriter/TableData |
| `cn.jiebaba.summe.office.excel` | Excel：XlsxReader/XlsxWriter/ExcelReader/ExcelWriter/Formula/ErrorValue |
| `cn.jiebaba.summe.office.docx` | Word：DocxReader/DocxWriter/Run |
| `cn.jiebaba.summe.office.pdf` | PDF：PdfReader/PdfWriter/TtfFont |
| `cn.jiebaba.summe.office.md` | Markdown：MarkdownReader/MarkdownWriter/MdHtml |
| `cn.jiebaba.summe.office.csv` | CSV：CsvReader/CsvWriter |
| `cn.jiebaba.summe.office.xml` | XML：XmlReader/XmlWriter |
| `cn.jiebaba.summe.office.ocr` | OCR：Ocr/OcrConfig/OcrResult + ONNX 引擎 + DB 后处理 |

## 模块约定

- **按需探测**：纯 JDK 的部分（csv/md/xml）零依赖运行；xlsx/docx/pdf 在 classpath 中检测到 Apache POI/PDFBox 时才激活
- **统一 API**：所有格式通过 `OfficeReader`/`OfficeWriter` 或 `TableReader`/`TableWriter` 统一接口访问
- **OCR**：基于 ONNX Runtime，包含 DB（文本检测）+ CRNN（文本识别）+ 分类器流水线
