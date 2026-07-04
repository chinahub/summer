package cn.jiebaba.summer.web.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

public final class WebResponse {
    private final OutputStream out;
    private int status = HttpStatus.OK.code();
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private byte[] body = new byte[0];
    private boolean committed = false;
    private boolean keepAlive = false;

    public WebResponse(OutputStream out) {
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

    public boolean committed() { return committed; }

    /**
     * 提交响应：补齐 Content-Type/Content-Length/Connection/Date 等默认头，
     * 写出 HTTP/1.1 状态行、响应头与响应体并刷新输出流；已提交则直接返回。
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
        headers.putIfAbsent("Connection", keepAlive ? "keep-alive" : "close");
        headers.putIfAbsent("Date", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)));

        HttpStatus statusEnum = HttpStatus.valueOf(status);
        StringBuilder head = new StringBuilder(128);
        head.append("HTTP/1.1 ").append(status).append(' ').append(statusEnum.reason()).append("\r\n");
        for (Map.Entry<String, String> e : headers.entrySet()) {
            head.append(e.getKey()).append(": ").append(e.getValue()).append("\r\n");
        }
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        if (body.length > 0) {
            out.write(body);
        }
        out.flush();
    }
}
