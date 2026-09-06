package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.web.http.WebResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

/** chunked 流式响应的字节序测试：头部、chunk 编码与终结块（基于内存通道）。 */
public class ChunkedResponseTest {

    /** 内存汇聚通道：所有写出字节收集到缓冲区。 */
    private static final class MemoryChannel implements WritableByteChannel {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        @Override
        public int write(ByteBuffer src) {
            byte[] data = new byte[src.remaining()];
            src.get(data);
            bos.writeBytes(data);
            return data.length;
        }

        @Override
        public boolean isOpen() { return true; }

        @Override
        public void close() {}
    }

    @Test
    public void chunkedHeadHasTransferEncodingAndNoContentLength() throws Exception {
        MemoryChannel ch = new MemoryChannel();
        WebResponse resp = new WebResponse(ch);
        resp.contentType("text/event-stream");
        resp.header("Cache-Control", "no-cache");
        resp.commitChunked();
        String head = new String(ch.bos.toByteArray(), StandardCharsets.UTF_8);
        Assertions.assertTrue(head.startsWith("HTTP/1.1 200 OK\r\n"), head);
        Assertions.assertTrue(head.contains("Transfer-Encoding: chunked\r\n"), head);
        Assertions.assertFalse(head.contains("Content-Length"), head);
        Assertions.assertTrue(head.contains("Content-Type: text/event-stream\r\n"), head);
        Assertions.assertTrue(head.endsWith("\r\n\r\n"));
        Assertions.assertTrue(resp.isChunked());
        Assertions.assertTrue(resp.committed());
    }

    @Test
    public void writeChunkProducesHexLengthFraming() throws Exception {
        MemoryChannel ch = new MemoryChannel();
        WebResponse resp = new WebResponse(ch);
        resp.commitChunked();
        int headEnd = new String(ch.bos.toByteArray(), StandardCharsets.UTF_8).indexOf("\r\n\r\n") + 4;
        resp.writeChunk("data: hi\n\n".getBytes(StandardCharsets.UTF_8));
        resp.writeChunk(new byte[0]);
        resp.finishChunked();
        String body = new String(ch.bos.toByteArray(), StandardCharsets.UTF_8).substring(headEnd);
        // "data: hi\n\n" 为 10 字节 → "a\r\n" + 数据 + "\r\n"，零长度 chunk 忽略，终结块 "0\r\n\r\n"
        Assertions.assertEquals("a\r\ndata: hi\n\n\r\n0\r\n\r\n", body);
    }

    @Test
    public void commitChunkedKeepsKeepAliveConnection() throws Exception {
        MemoryChannel ch = new MemoryChannel();
        WebResponse resp = new WebResponse(ch);
        resp.keepAlive(true);
        resp.commitChunked();
        String head = new String(ch.bos.toByteArray(), StandardCharsets.UTF_8);
        Assertions.assertTrue(head.contains("Connection: keep-alive\r\n"), head);
    }

    @Test
    public void writeChunkWithoutCommitRejected() {
        MemoryChannel ch = new MemoryChannel();
        WebResponse resp = new WebResponse(ch);
        Assertions.assertThrows(IllegalStateException.class, () -> resp.writeChunk(new byte[]{1}));
        Assertions.assertThrows(IllegalStateException.class, resp::finishChunked);
    }

    @Test
    public void plainCommitUnaffectedAfterRefactor() throws Exception {
        MemoryChannel ch = new MemoryChannel();
        WebResponse resp = new WebResponse(ch);
        resp.body("hello");
        resp.commit();
        String raw = new String(ch.bos.toByteArray(), StandardCharsets.UTF_8);
        Assertions.assertTrue(raw.contains("Content-Length: 5\r\n"), raw);
        Assertions.assertFalse(raw.contains("Transfer-Encoding"), raw);
        Assertions.assertTrue(raw.endsWith("hello"));
    }
}
