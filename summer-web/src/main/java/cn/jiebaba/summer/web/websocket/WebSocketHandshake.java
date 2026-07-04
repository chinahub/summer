package cn.jiebaba.summer.web.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 处理 WebSocket 开场握手（RFC 6455 第 4 节）。
 * 检测升级请求并写出 101 响应。
 */
public final class WebSocketHandshake {

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private WebSocketHandshake() {}

    /** 当 HTTP 请求为 WebSocket 升级请求时返回 true。 */
    public static boolean isUpgradeRequest(Map<String, List<String>> headers, String method) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        String upgrade = firstHeader(headers, "upgrade");
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim());
    }

    /**
     * 写出 101 Switching Protocols 响应以完成握手。
     * @return 成功时返回 true
     */
    public static boolean completeHandshake(Map<String, List<String>> headers, OutputStream out) throws IOException {
        String key = firstHeader(headers, "sec-websocket-key");
        if (key == null || key.isBlank()) return false;
        String accept = computeAccept(key);
        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 101 Switching Protocols\r\n");
        sb.append("Upgrade: websocket\r\n");
        sb.append("Connection: Upgrade\r\n");
        sb.append("Sec-WebSocket-Accept: ").append(accept).append("\r\n");
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
        return true;
    }

    public static String computeAccept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update((key + MAGIC).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sha1.digest());
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    private static String firstHeader(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
