package cn.jiebaba.summer.web.http;

import cn.jiebaba.summer.web.server.MaxUploadSizeExceededException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 socket 流读取的极简 HTTP/1.1 请求解析器。
 * 支持请求行、请求头与请求体；请求体按 Content-Length 或 Transfer-Encoding: chunked 读取。
 * 提供基于流与基于阻塞通道两种解析入口。
 */
public final class RawHttpRequest {

    /** chunk-size 行与 trailer 行的最大长度，防御恶意超长行。 */
    private static final int MAX_LINE = 8192;

    private final String method;
    private final String target;   // 原始请求目标（path?query）
    private final String protocol;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    private RawHttpRequest(String method, String target, String protocol,
                           Map<String, List<String>> headers, byte[] body) {
        this.method = method;
        this.target = target;
        this.protocol = protocol;
        this.headers = headers;
        this.body = body;
    }

    public String method() { return method; }
    public String target() { return target; }
    public String protocol() { return protocol; }
    public Map<String, List<String>> headers() { return headers; }
    public byte[] body() { return body; }

    /**
     * 从输入流解析一个 HTTP/1.1 请求：读取请求头块、解析请求行与各请求头，
     * 再依据 Content-Length 或 Transfer-Encoding: chunked 读取请求体。
     * 保留此重载以兼容基于流的测试与工具。
     *
     * @param raw            底层输入流
     * @param maxHeaderSize  请求头最大字节数
     * @param maxRequestSize 请求体最大字节数
     * @return 解析得到的请求对象
     * @throws IOException 当流关闭、请求格式错误或超出大小限制时抛出
     */
    public static RawHttpRequest parse(InputStream raw, int maxHeaderSize, int maxRequestSize) throws IOException {
        BufferedInputStream in = raw instanceof BufferedInputStream bi ? bi : new BufferedInputStream(raw);
        byte[] headerBlock = readHeaderBlock(in, maxHeaderSize);
        if (headerBlock.length == 0) {
            throw new IOException("empty request (connection closed)");
        }
        Map<String, List<String>> headers = parseHeaders(headerBlock);
        byte[] body = readBody(in, headers, maxRequestSize);
        return build(headerBlock, headers, body);
    }

    /**
     * 从阻塞 {@link ReadableByteChannel} 解析一个 HTTP/1.1 请求：以块为单位读入 {@code buf}
     * （可含上一请求的残留字节），扫描请求头块结束符后按 Content-Length 或
     * Transfer-Encoding: chunked 读取请求体。解析完成后 {@code buf} 中保留尚未消费的字节，
     * 供 keep-alive 的下一请求或 WebSocket 升级复用。该方法运行在虚拟线程上，阻塞读取会卸载载体线程。
     *
     * @param ch             阻塞模式通道
     * @param buf            连接级复用缓冲（position..limit 为未消费数据，可为空）
     * @param maxHeaderSize  请求头最大字节数
     * @param maxRequestSize 请求体最大字节数
     * @return 解析得到的请求对象
     * @throws IOException 当通道关闭、请求格式错误或超出大小限制时抛出
     */
    public static RawHttpRequest parse(ReadableByteChannel ch, ByteBuffer buf, int maxHeaderSize, int maxRequestSize) throws IOException {
        byte[] headerBlock = readHeaderBlock(ch, buf, maxHeaderSize);
        Map<String, List<String>> headers = parseHeaders(headerBlock);
        byte[] body = readBody(ch, buf, headers, maxRequestSize);
        return build(headerBlock, headers, body);
    }

    /** 由请求头块与请求体构造请求对象，解析请求行得到方法/目标/协议。 */
    private static RawHttpRequest build(byte[] headerBlock, Map<String, List<String>> headers, byte[] body) throws IOException {
        String headerText = new String(headerBlock, StandardCharsets.UTF_8);
        String[] lines = headerText.split("\r\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new IOException("malformed request line");
        }
        String[] requestLine = lines[0].split(" ", 3);
        if (requestLine.length < 2) {
            throw new IOException("malformed request line: " + lines[0]);
        }
        String method = requestLine[0].toUpperCase();
        String target = requestLine[1];
        String protocol = requestLine.length > 2 ? requestLine[2] : "HTTP/1.1";
        return new RawHttpRequest(method, target, protocol, headers, body);
    }

    /** 解析请求头块为头部映射（键名小写）；不解析请求行。 */
    private static Map<String, List<String>> parseHeaders(byte[] headerBlock) {
        String[] lines = new String(headerBlock, StandardCharsets.UTF_8).split("\r\n");
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return headers;
    }

    /**
     * 读取请求头块，直到遇到空行（{@code \r\n\r\n}）为止，并在超出最大长度时抛出异常。
     *
     * @param in           输入流
     * @param maxHeaderSize 请求头最大字节数
     * @return 不含末尾空行的请求头字节
     * @throws IOException 当读取失败或超出大小限制时抛出
     */
    private static byte[] readHeaderBlock(InputStream in, int maxHeaderSize) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(512);
        int[] win = new int[4];
        int n = 0;
        int b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            if (buf.size() > maxHeaderSize) {
                throw new IOException("Header size exceeds maximum allowed " + maxHeaderSize + " bytes");
            }
            win[n++ & 3] = b;
            if (n >= 4) {
                int i0 = win[(n - 4) & 3], i1 = win[(n - 3) & 3], i2 = win[(n - 2) & 3], i3 = win[(n - 1) & 3];
                if (i0 == '\r' && i1 == '\n' && i2 == '\r' && i3 == '\n') {
                    break;
                }
            }
        }
        byte[] all = buf.toByteArray();
        if (all.length < 4) return all;
        byte[] header = new byte[all.length - 4];
        System.arraycopy(all, 0, header, 0, header.length);
        return header;
    }

    /**
     * 从通道与复用缓冲读取请求头块，直到遇到空行（{@code \r\n\r\n}）为止。
     * 优先消费 {@code buf} 中已有的残留字节，耗尽后以块为单位从通道填充。
     *
     * @param ch            阻塞通道
     * @param buf           连接级复用缓冲
     * @param maxHeaderSize 请求头最大字节数
     * @return 不含末尾空行的请求头字节
     * @throws IOException 当通道关闭、读取失败或超出大小限制时抛出
     */
    private static byte[] readHeaderBlock(ReadableByteChannel ch, ByteBuffer buf, int maxHeaderSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        int[] win = new int[4];
        int n = 0;
        while (true) {
            if (!buf.hasRemaining()) {
                buf.clear();
                int r = ch.read(buf);
                if (r == -1) {
                    if (out.size() == 0) throw new IOException("empty request (connection closed)");
                    throw new IOException("unexpected EOF while reading headers");
                }
                buf.flip();
            }
            int b = buf.get() & 0xFF;
            out.write(b);
            if (out.size() > maxHeaderSize) {
                throw new IOException("Header size exceeds maximum allowed " + maxHeaderSize + " bytes");
            }
            win[n++ & 3] = b;
            if (n >= 4) {
                int i0 = win[(n - 4) & 3], i1 = win[(n - 3) & 3], i2 = win[(n - 2) & 3], i3 = win[(n - 1) & 3];
                if (i0 == '\r' && i1 == '\n' && i2 == '\r' && i3 == '\n') {
                    break;
                }
            }
        }
        byte[] all = out.toByteArray();
        byte[] header = new byte[all.length - 4];
        System.arraycopy(all, 0, header, 0, header.length);
        return header;
    }

    private static byte[] readBody(InputStream in, Map<String, List<String>> headers, int maxRequestSize) throws IOException {
        if (isChunked(headers)) return readChunked(new StreamBodySource(in), maxRequestSize);
        int length = contentLength(headers);
        if (length <= 0) return new byte[0];
        if (length > maxRequestSize) {
            throw new MaxUploadSizeExceededException(length, maxRequestSize);
        }
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int r = in.read(body, read, length - read);
            if (r == -1) break;
            read += r;
        }
        if (read < length) {
            byte[] exact = new byte[read];
            System.arraycopy(body, 0, exact, 0, read);
            return exact;
        }
        return body;
    }

    /**
     * 按 Content-Length 从通道与复用缓冲读取请求体；优先消费 {@code buf} 残留字节。
     * 若为 Transfer-Encoding: chunked 则转交 {@link #readChunked} 解码。
     *
     * @param ch             阻塞通道
     * @param buf            连接级复用缓冲
     * @param headers        请求头（用于取 Content-Length / Transfer-Encoding）
     * @param maxRequestSize 请求体最大字节数
     * @return 请求体字节
     * @throws IOException 当读取失败或超出大小限制时抛出
     */
    private static byte[] readBody(ReadableByteChannel ch, ByteBuffer buf, Map<String, List<String>> headers, int maxRequestSize) throws IOException {
        if (isChunked(headers)) return readChunked(new ChannelBodySource(ch, buf), maxRequestSize);
        int length = contentLength(headers);
        if (length <= 0) return new byte[0];
        if (length > maxRequestSize) {
            throw new MaxUploadSizeExceededException(length, maxRequestSize);
        }
        byte[] body = new byte[length];
        int read = 0;
        while (read < length && buf.hasRemaining()) {
            body[read++] = buf.get();
        }
        while (read < length) {
            buf.clear();
            int r = ch.read(buf);
            if (r == -1) break;
            buf.flip();
            while (read < length && buf.hasRemaining()) {
                body[read++] = buf.get();
            }
        }
        if (read < length) {
            byte[] exact = new byte[read];
            System.arraycopy(body, 0, exact, 0, read);
            return exact;
        }
        return body;
    }

    /** 是否使用 Transfer-Encoding: chunked 编码请求体。 */
    private static boolean isChunked(Map<String, List<String>> headers) {
        List<String> values = headers.get("transfer-encoding");
        if (values == null || values.isEmpty()) return false;
        return values.get(0).trim().equalsIgnoreCase("chunked");
    }

    /**
     * 按 Transfer-Encoding: chunked 解码请求体：循环读取 chunk-size 行与 chunk 数据，
     * 直到 0 长度块；累计大小超过 maxRequestSize 时抛出异常。
     *
     * @param src            统一体读取源
     * @param maxRequestSize 请求体最大字节数
     * @return 解码后的完整请求体字节
     * @throws IOException 当格式错误、EOF 或超出大小限制时抛出
     */
    private static byte[] readChunked(BodySource src, int maxRequestSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(512);
        while (true) {
            String line = src.readLine();
            if (line == null) throw new IOException("unexpected EOF reading chunk size");
            int semi = line.indexOf(';'); // chunk-size 可带扩展：size[;ext]
            String sizeHex = (semi >= 0 ? line.substring(0, semi) : line).trim();
            int size;
            try {
                size = Integer.parseInt(sizeHex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("invalid chunk size: " + line);
            }
            if (size == 0) {
                // 读取可能的 trailers 与结束空行
                String trailer;
                do {
                    trailer = src.readLine();
                } while (trailer != null && !trailer.isEmpty());
                break;
            }
            if (size < 0) throw new IOException("invalid chunk size: " + size);
            if (out.size() + size > maxRequestSize) {
                throw new MaxUploadSizeExceededException(out.size() + size, maxRequestSize);
            }
            byte[] chunk = new byte[size];
            int read = src.read(chunk, 0, size);
            if (read < size) throw new IOException("unexpected EOF reading chunk data");
            out.write(chunk, 0, read);
            String sep = src.readLine(); // chunk 数据后的 CRLF
            if (sep == null || !sep.isEmpty()) throw new IOException("missing CRLF after chunk data");
        }
        return out.toByteArray();
    }

    private static int contentLength(Map<String, List<String>> headers) {
        List<String> values = headers.get("content-length");
        if (values == null || values.isEmpty()) return 0;
        try {
            return Integer.parseInt(values.get(0).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 请求体读取源：统一流与通道两种底层读取的行/字节读取。 */
    private interface BodySource {
        /** 读取一行（至 \r\n），返回不含 CRLF 的内容；遇 EOF 且无数据时返回 null。 */
        String readLine() throws IOException;
        /** 尽量读取 len 字节填入 dst[off..]，返回实际读取数；一开始即 EOF 返回 -1。 */
        int read(byte[] dst, int off, int len) throws IOException;
    }

    /** 基于 InputStream 的体读取源。 */
    private static final class StreamBodySource implements BodySource {
        private final InputStream in;
        StreamBodySource(InputStream in) { this.in = in; }

        @Override
        public String readLine() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64);
            int prev = -1;
            int b;
            while ((b = in.read()) != -1) {
                if (prev == '\r' && b == '\n') {
                    byte[] all = out.toByteArray();
                    return new String(all, 0, all.length - 1, StandardCharsets.UTF_8);
                }
                out.write(b);
                prev = b;
                if (out.size() > MAX_LINE) throw new IOException("chunk line too long");
            }
            return out.size() == 0 ? null : new String(out.toByteArray(), StandardCharsets.UTF_8);
        }

        @Override
        public int read(byte[] dst, int off, int len) throws IOException {
            if (len == 0) return 0;
            int read = 0;
            while (read < len) {
                int r = in.read(dst, off + read, len - read);
                if (r == -1) return read == 0 ? -1 : read;
                read += r;
            }
            return read;
        }
    }

    /** 基于 ReadableByteChannel + 复用缓冲的体读取源。 */
    private static final class ChannelBodySource implements BodySource {
        private final ReadableByteChannel ch;
        private final ByteBuffer buf;
        ChannelBodySource(ReadableByteChannel ch, ByteBuffer buf) { this.ch = ch; this.buf = buf; }

        @Override
        public String readLine() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64);
            int prev = -1;
            while (true) {
                int b = readByte();
                if (b == -1) {
                    return out.size() == 0 ? null : new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
                if (prev == '\r' && b == '\n') {
                    byte[] all = out.toByteArray();
                    return new String(all, 0, all.length - 1, StandardCharsets.UTF_8);
                }
                out.write(b);
                prev = b;
                if (out.size() > MAX_LINE) throw new IOException("chunk line too long");
            }
        }

        @Override
        public int read(byte[] dst, int off, int len) throws IOException {
            if (len == 0) return 0;
            int read = 0;
            while (read < len) {
                if (!buf.hasRemaining()) {
                    buf.clear();
                    int r = ch.read(buf);
                    if (r == -1) return read == 0 ? -1 : read;
                    buf.flip();
                }
                int n = Math.min(len - read, buf.remaining());
                buf.get(dst, off + read, n);
                read += n;
            }
            return read;
        }

        /** 从复用缓冲读单字节，耗尽后从通道块读填充；-1 表示 EOF。 */
        private int readByte() throws IOException {
            if (!buf.hasRemaining()) {
                buf.clear();
                int r = ch.read(buf);
                if (r == -1) return -1;
                buf.flip();
            }
            return buf.get() & 0xFF;
        }
    }
}
