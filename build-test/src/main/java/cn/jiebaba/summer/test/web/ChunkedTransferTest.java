package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.web.http.RawHttpRequest;
import cn.jiebaba.summer.web.server.MaxUploadSizeExceededException;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.charset.StandardCharsets;

/**
 * 验证 Transfer-Encoding: chunked 请求体解码：流路径与阻塞通道路径（经 Pipe）一致，
 * 覆盖多块拼接、chunk 扩展忽略、空 body 与超限拒绝。
 */
public class ChunkedTransferTest {

    private static final String HEAD = "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n";

    /** 构造分块请求体：两个数据块（第二块带 chunk 扩展）+ 结束块，拼成完整 HTTP 请求字节。 */
    private static byte[] chunkedRequest(String part1, String part2) {
        StringBuilder sb = new StringBuilder(HEAD);
        appendChunk(sb, part1, null);
        appendChunk(sb, part2, "name=ext"); // 带 chunk 扩展，应被忽略
        sb.append("0\r\n\r\n"); // 结束块 + 空 trailer 行
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendChunk(StringBuilder sb, String data, String ext) {
        sb.append(Integer.toHexString(data.length()));
        if (ext != null) sb.append(';').append(ext);
        sb.append("\r\n").append(data).append("\r\n");
    }

    @Test
    void chunkedBodyDecodedFromStream() throws Throwable {
        byte[] raw = chunkedRequest("Hello, ", "summer!");
        RawHttpRequest req = RawHttpRequest.parse(new ByteArrayInputStream(raw), 8192, 8388608);
        Assert.assertEquals("Hello, summer!", new String(req.body(), StandardCharsets.UTF_8));
        Assert.assertEquals("POST", req.method());
        Assert.assertEquals("/echo", req.target());
    }

    @Test
    void chunkedBodyDecodedFromChannel() throws Throwable {
        byte[] raw = chunkedRequest("Hello, ", "summer!");
        Pipe pipe = Pipe.open();
        // 虚拟线程写入完整请求并关闭 sink，使 source 在缓冲排空后读到 EOF
        Thread writer = Thread.startVirtualThread(() -> {
            try (var sink = pipe.sink()) {
                ByteBuffer b = ByteBuffer.wrap(raw);
                while (b.hasRemaining()) {
                    sink.write(b);
                }
            } catch (Exception ignore) {
                // 写入失败由解析端的断言暴露
            }
        });
        ByteBuffer buf = ByteBuffer.allocate(8192);
        buf.limit(0); // 初始为空，首次读取时从通道填充
        RawHttpRequest req = RawHttpRequest.parse(pipe.source(), buf, 8192, 8388608);
        Assert.assertEquals("Hello, summer!", new String(req.body(), StandardCharsets.UTF_8));
        writer.join();
    }

    @Test
    void emptyChunkedBody() throws Throwable {
        byte[] raw = (HEAD + "0\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        RawHttpRequest req = RawHttpRequest.parse(new ByteArrayInputStream(raw), 8192, 8388608);
        Assert.assertEquals(0, req.body().length);
    }

    @Test
    void chunkedExceedsMaxSize() {
        byte[] raw = chunkedRequest("0123456789", "0123456789"); // 20 字节
        MaxUploadSizeExceededException e = Assert.assertThrows(MaxUploadSizeExceededException.class,
                () -> RawHttpRequest.parse(new ByteArrayInputStream(raw), 8192, 8));
        Assert.assertTrue(e.actualSize() == 10 && e.maxSize() == 8,
                "应报告首个 chunk 的大小 10 超过上限 8");
    }
}
