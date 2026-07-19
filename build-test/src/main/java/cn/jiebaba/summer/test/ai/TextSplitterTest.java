package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.document.Document;
import cn.jiebaba.summer.ai.document.TokenTextSplitter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 文档分块工具的单元测试。 */
public class TextSplitterTest {

    @Test
    public void emptyInputReturnsEmpty() {
        TokenTextSplitter splitter = new TokenTextSplitter(10, 2);
        Assertions.assertTrue(splitter.split("").isEmpty());
        Assertions.assertTrue(splitter.split((String) null).isEmpty());
    }

    @Test
    public void chunkSizeAndOverlap() {
        TokenTextSplitter splitter = new TokenTextSplitter(3, 1);
        List<String> chunks = splitter.split("a b c d e f");
        Assertions.assertTrue(chunks.size() >= 2, "至少分两块，实际: " + chunks.size());
        Assertions.assertEquals("a b c", chunks.get(0));
        Assertions.assertEquals("c d e", chunks.get(1));
    }

    @Test
    public void chineseTokenSplit() {
        TokenTextSplitter splitter = new TokenTextSplitter(4, 0);
        List<String> chunks = splitter.split("你好 世界 夏天 快乐 编程");
        Assertions.assertTrue(chunks.size() >= 2);
        Assertions.assertTrue(chunks.get(0).contains("你好"));
    }

    @Test
    public void splitDocumentCarriesMetadata() {
        TokenTextSplitter splitter = new TokenTextSplitter(2, 0);
        Document doc = Document.builder().id("d1").content("one two three four").metadata("source", "test").build();
        List<Document> docs = splitter.split(doc);
        Assertions.assertFalse(docs.isEmpty());
        for (Document d : docs) {
            Assertions.assertEquals("d1", d.id());
            Assertions.assertEquals("test", d.metadata().get("source"));
            Assertions.assertNotNull(d.metadata().get("chunkIndex"));
        }
    }

    @Test
    public void invalidArgsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new TokenTextSplitter(0, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new TokenTextSplitter(5, 5));
    }
}
