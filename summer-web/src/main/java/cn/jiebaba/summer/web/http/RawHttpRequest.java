package cn.jiebaba.summer.web.http;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 socket 流读取的极简 HTTP/1.1 请求解析器。
 * 支持请求行、请求头与基于 Content-Length 的请求体；按设计不支持分块传输编码
 * （在 JSON API 中较少使用）。
 */
public final class RawHttpRequest {

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
     * 再依据 Content-Length 读取请求体。
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

        byte[] body = readBody(in, headers, maxRequestSize);
        return new RawHttpRequest(method, target, protocol, headers, body);
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

    private static byte[] readBody(InputStream in, Map<String, List<String>> headers, int maxRequestSize) throws IOException {
        int length = contentLength(headers);
        if (length <= 0) return new byte[0];
        if (length > maxRequestSize) {
            throw new IOException("Request body size " + length + " exceeds maximum allowed " + maxRequestSize + " bytes");
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

    private static int contentLength(Map<String, List<String>> headers) {
        List<String> values = headers.get("content-length");
        if (values == null || values.isEmpty()) return 0;
        try {
            return Integer.parseInt(values.get(0).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
