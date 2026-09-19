package cn.jiebaba.summer.office.xml;

import cn.jiebaba.summer.office.OfficeException;
import cn.jiebaba.summer.office.OfficeWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * XML 写入器：纯 JDK 实现，使用 StAX（{@link XMLStreamWriter}）生成 XML。
 * <p>将文本内容包装为 {@code <document><content>...</content></document>} 结构；
 * 更复杂的结构化生成请直接使用 JDK StAX/DOM API。
 */
public class XmlWriter implements OfficeWriter {

    private final String rootElement;
    private final String contentElement;

    public XmlWriter() {
        this("document", "content");
    }

    /** 指定根元素名与内容元素名。 */
    public XmlWriter(String rootElement, String contentElement) {
        this.rootElement = rootElement;
        this.contentElement = contentElement;
    }

    /** 将文本内容包装为 XML 文档（根元素+内容元素），用 StAX 流式写出并编码为 UTF-8 字节。 */
    @Override
    public byte[] write(String content) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newFactory();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out, "UTF-8");
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");
            writer.writeStartElement(rootElement);
            writer.writeCharacters("\n  ");
            writer.writeStartElement(contentElement);
            writer.writeCharacters(content == null ? "" : content);
            writer.writeEndElement();
            writer.writeCharacters("\n");
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new OfficeException("XML 生成失败", e);
        }
        return out.toByteArray();
    }
}
