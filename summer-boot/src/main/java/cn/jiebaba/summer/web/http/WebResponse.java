package cn.jiebaba.summer.web.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public final class WebResponse {
    private final WritableByteChannel out;
    private int status = HttpStatus.OK.code();
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private byte[] body = new byte[0];
    private boolean committed = false;
    private boolean chunked = false;
    private boolean keepAlive = false;

    public WebResponse(WritableByteChannel out) {
        this.out = out;
    }

    public WebResponse status(int code) { this.status = code; return this; }
    public int status() { return status; }

    public WebResponse keepAlive(boolean keepAlive) { this.keepAlive = keepAlive; return this; }
    public boolean keepAlive() { return keepAlive; }

    public WebResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public String header(String name) {
        return headers.get(name);
    }

    public WebResponse contentType(String type) {
        headers.put("Content-Type", type);
        return this;
    }

    public WebResponse body(String text) {
        this.body = text.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public WebResponse body(byte[] bytes) {
        this.body = bytes;
        return this;
    }

    /** 当前响应体字节数组（只读视图，供压缩等提交前处理使用）。 */
    public byte[] body() {
        return body;
    }

    public boolean committed() { return committed; }

    /**
     * 提交响应：补齐 Content-Type/Content-Length/Connection/Date 等默认头，
     * 写出 HTTP/1.1 状态行、响应头与响应体；已提交则直接返回。
     * 当底层 sink 为 {@link GatheringByteChannel}（生产环境的 SocketChannel）时，
     * 以聚集写（vectored write）合并头与体一次性发出以减少系统调用；否则回退顺序写。
     *
     * @throws IOException 写出响应时发生 I/O 错误
     */
    public void commit() throws IOException {
        if (committed) return;
        committed = true;
        if (!headers.containsKey("Content-Type")) {
            headers.put("Content-Type", MediaType.APPLICATION_JSON_UTF8);
        }
        headers.putIfAbsent("Content-Length", Integer.toString(body.length));
        ByteBuffer headBuf = buildHead();
        ByteBuffer bodyBuf = body.length > 0 ? ByteBuffer.wrap(body) : null;
        if (out instanceof GatheringByteChannel g) {
            writeGathering(g, bodyBuf == null ? new ByteBuffer[]{headBuf} : new ByteBuffer[]{headBuf, bodyBuf});
        } else {
            writeSequential(headBuf);
            if (bodyBuf != null) writeSequential(bodyBuf);
        }
    }

    /**
     * 以 chunked 分块模式提交响应头：不写 Content-Length，代之以
     * {@code Transfer-Encoding: chunked}（保持 keep-alive 连接复用），
     * 之后经 {@link #writeChunk(byte[])} 增量写、{@link #finishChunked()} 收尾。
     * 适用于 SSE（text/event-stream）等长度未知的流式响应。
     *
     * @throws IOException 写出响应头时发生 I/O 错误
     */
    public void commitChunked() throws IOException {
        if (committed) return;
        committed = true;
        chunked = true;
        if (!headers.containsKey("Content-Type")) {
            headers.put("Content-Type", MediaType.APPLICATION_JSON_UTF8);
        }
        writeSequential(buildHead());
    }

    /** 是否处于 chunked 流式模式。 */
    public boolean isChunked() {
        return chunked;
    }

    /**
     * 写出一个 chunk：{@code <hex 长度>\r\n<data>\r\n}；零长度数据忽略。
     * 仅在 commitChunked 之后有效。
     *
     * @throws IOException 写出失败
     */
    public void writeChunk(byte[] data) throws IOException {
        if (!chunked) {
            throw new IllegalStateException("Response not in chunked mode: call commitChunked() first");
        }
        if (data == null || data.length == 0) return;
        byte[] size = (Integer.toHexString(data.length) + "\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        if (out instanceof GatheringByteChannel g) {
            writeGathering(g, new ByteBuffer[]{ByteBuffer.wrap(size), ByteBuffer.wrap(data), ByteBuffer.wrap(crlf)});
        } else {
            writeSequential(ByteBuffer.wrap(size));
            writeSequential(ByteBuffer.wrap(data));
            writeSequential(ByteBuffer.wrap(crlf));
        }
    }

    /** 结束 chunked 流：写出终结块 {@code 0\r\n\r\n}。仅在 commitChunked 之后有效。 */
    public void finishChunked() throws IOException {
        if (!chunked) {
            throw new IllegalStateException("Response not in chunked mode: call commitChunked() first");
        }
        writeSequential(ByteBuffer.wrap("0\r\n\r\n".getBytes(StandardCharsets.UTF_8)));
    }

    /** 拼装状态行与响应头缓冲（chunked 模式追加 Transfer-Encoding 且不写 Content-Length），并补齐 Connection/Date 默认头。 */
    private ByteBuffer buildHead() {
        headers.putIfAbsent("Connection", keepAlive ? "keep-alive" : "close");
        headers.putIfAbsent("Date", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)));
        HttpStatus statusEnum = HttpStatus.valueOf(status);
        StringBuilder head = new StringBuilder(128);
        head.append("HTTP/1.1 ").append(status).append(' ').append(statusEnum.reason()).append("\r\n");
        if (chunked) {
            head.append("Transfer-Encoding: chunked\r\n");
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            head.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        }
        head.append("\r\n");
        return ByteBuffer.wrap(head.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 以聚集写循环写出所有缓冲，处理可能的部分写。 */
    private static void writeGathering(GatheringByteChannel ch, ByteBuffer[] buffers) throws IOException {
        while (hasRemaining(buffers)) {
            ch.write(buffers);
        }
    }

    /** 顺序写单个缓冲至完成，用于非聚集 sink（如测试用的流包装通道）。 */
    private void writeSequential(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            out.write(buffer);
        }
    }

    private static boolean hasRemaining(ByteBuffer[] buffers) {
        for (ByteBuffer b : buffers) {
            if (b.hasRemaining()) return true;
        }
        return false;
    }
}
