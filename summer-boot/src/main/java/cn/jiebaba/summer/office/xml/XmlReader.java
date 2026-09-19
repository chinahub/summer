package cn.jiebaba.summer.office.xml;

import cn.jiebaba.summer.office.OfficeException;
import cn.jiebaba.summer.office.OfficeReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * XML 读取器：纯 JDK 实现，使用 StAX（{@link XMLStreamReader}）流式解析。
 * <p>提取所有元素的文本内容并拼接为纯文本，保留层级缩进。
 * 不保留原始标签结构；如需结构化访问请直接使用 JDK DOM/StAX API。
 */
public class XmlReader implements OfficeReader {

    /** 从输入流解析 XML，按 StAX 事件流提取元素文本内容，保留层级缩进拼接为纯文本；禁用外部实体与 DTD 以防 XXE 攻击。 */
    @Override
    public String read(InputStream in) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        StringBuilder result = new StringBuilder();
        Deque<String> path = new ArrayDeque<>();
        try (InputStream stream = in) {
            XMLStreamReader reader = factory.createXMLStreamReader(stream);
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        path.push(reader.getLocalName());
                        if (!result.isEmpty()) {
                            result.append('\n');
                        }
                        result.append("  ".repeat(path.size() - 1));
                        result.append(reader.getLocalName()).append(": ");
                    }
                    case XMLStreamConstants.CHARACTERS -> {
                        String text = reader.getText().strip();
                        if (!text.isEmpty()) {
                            result.append(text);
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        if (!path.isEmpty()) {
                            path.pop();
                        }
                    }
                    default -> { }
                }
            }
            reader.close();
        } catch (XMLStreamException e) {
            throw new OfficeException("XML 解析失败", e);
        }
        return result.toString();
    }
}
