package cn.jiebaba.summer.office.docx;

import cn.jiebaba.summer.office.OfficeException;
import cn.jiebaba.summer.office.OfficeReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * DOCX 读取器：纯 JDK 实现（java.util.zip + StAX），从 .docx 提取纯文本，零第三方依赖。
 * <p>DOCX 本质为 ZIP + OOXML XML；本类将输入流拷贝到临时文件后以 ZipFile 打开，
 * StAX 解析 {@code word/document.xml}，提取 {@code <w:t>} 文本元素，
 * 段落（{@code <w:p>}）以换行分隔，表格单元格（{@code <w:tc>}）以制表符分隔。
 *
 * <pre>{@code
 * String text = new DocxReader().read(inputStream);
 * }</pre>
 */
public class DocxReader implements OfficeReader {

    @Override
    public String read(InputStream in) throws IOException {
        Path tempFile = Files.createTempFile("summer-docx-read-", ".docx");
        try {
            try (InputStream src = in; var out = Files.newOutputStream(tempFile)) {
                src.transferTo(out);
            }
            try (ZipFile zip = new ZipFile(tempFile.toFile())) {
                ZipEntry entry = zip.getEntry("word/document.xml");
                if (entry == null) {
                    throw new OfficeException("缺少 word/document.xml，非合法 DOCX");
                }
                try (InputStream docIn = zip.getInputStream(entry)) {
                    return parseDocument(docIn);
                }
            }
        } catch (XMLStreamException e) {
            throw new OfficeException("DOCX 解析失败", e);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /** StAX 解析 document.xml：遍历元素事件，提取 w:t 文本，按 w:p/w:tr 换行、w:tc 制表。 */
    private static String parseDocument(InputStream in) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        XMLStreamReader reader = factory.createXMLStreamReader(in);
        StringBuilder result = new StringBuilder();
        StringBuilder text = new StringBuilder();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT && "t".equals(reader.getLocalName())) {
                text.append(reader.getElementText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                String name = reader.getLocalName();
                switch (name) {
                    case "p" -> {
                        if (!result.isEmpty()) {
                            result.append('\n');
                        }
                        result.append(text);
                        text.setLength(0);
                    }
                    case "tc" -> {
                        text.append('\t');
                    }
                    case "tr" -> {
                        if (text.length() > 0 && text.charAt(text.length() - 1) == '\t') {
                            text.setLength(text.length() - 1);
                        }
                        text.append('\n');
                    }
                    default -> { }
                }
            }
        }
        if (text.length() > 0) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            result.append(text);
        }
        return result.toString();
    }
}
