package cn.jiebaba.summer.sample;

import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.web.websocket.WebSocketHandshake;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * In-process WebSocket smoke test: starts the app, opens a raw TCP socket,
 * performs the WebSocket handshake, and verifies echo round-trip.
 */
public class WebSocketSmokeTest {

    private static int passed = 0;

    public static void main(String[] args) throws Exception {
        SummerApplication app = SummerApplication.run(Application.class, args);
        int port = app.webServer().port();
        Thread.sleep(500);
        try {
            // WebSocket handshake
            String key = "dGhlIHNhbXBsZSBub25jZQ==";
            try (Socket s = new Socket("127.0.0.1", port)) {
                s.setSoTimeout(5000);
                OutputStream out = s.getOutputStream();
                String req = "GET /ws/echo HTTP/1.1\r\n"
                        + "Host: 127.0.0.1\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: " + key + "\r\n"
                        + "Sec-WebSocket-Version: 13\r\n\r\n";
                out.write(req.getBytes(StandardCharsets.UTF_8));
                out.flush();

                String resp = readResponse(s.getInputStream());
                expect("101 switch protocols", true, resp.startsWith("HTTP/1.1 101"));
                String accept = WebSocketHandshake.computeAccept(key);
                expect("sec-websocket-accept present", true, resp.contains("Sec-WebSocket-Accept: " + accept));

                // read the "connected" message sent on @OnOpen
                String connected = readTextFrame(s.getInputStream());
                expect("onOpen message 'connected'", "connected", connected);

                // send a text frame (masked, as client must)
                sendTextFrame(out, "hello summer");
                String echo = readTextFrame(s.getInputStream());
                expect("echo response", "echo: hello summer", echo);

                // send binary frame and verify server still alive (ping/pong)
                sendPingFrame(out, new byte[]{1, 2, 3});
                String pong = readTextFrameOrPong(s.getInputStream());
                expect("pong or response received", true, pong != null);
            }
        } finally {
            app.webServer().stop();
            app.context().close();
        }
        System.out.println();
        System.out.println("WebSocket smoke test: " + passed + " assertions passed");
    }

    static String readResponse(InputStream raw) throws Exception {
        BufferedInputStream in = raw instanceof BufferedInputStream bi ? bi : new BufferedInputStream(raw);
        ByteArrayOutputStream2 buf = new ByteArrayOutputStream2();
        int[] win = new int[4];
        int n = 0, b;
        while ((b = in.read()) != -1) {
            buf.write(b);
            win[n++ & 3] = b;
            if (n >= 4) {
                if (win[(n - 4) & 3] == '\r' && win[(n - 3) & 3] == '\n'
                        && win[(n - 2) & 3] == '\r' && win[(n - 1) & 3] == '\n') break;
            }
        }
        return buf.toStr(StandardCharsets.UTF_8);
    }

    static String readTextFrame(InputStream in) throws Exception {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 == -1 || b1 == -1) return null;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) {
            int hi = in.read(), lo = in.read();
            len = ((hi & 0xFF) << 8) | (lo & 0xFF);
        } else if (len == 127) {
            for (int i = 0; i < 8; i++) in.read();
            len = 0; // not expected in test
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            in.read(mask);
        }
        byte[] payload = new byte[(int) len];
        in.read(payload);
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    static String readTextFrameOrPong(InputStream in) throws Exception {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 == -1 || b1 == -1) return null;
        int opcode = b0 & 0x0F;
        long len = b1 & 0x7F;
        if (len == 126) {
            int hi = in.read(), lo = in.read();
            len = ((hi & 0xFF) << 8) | (lo & 0xFF);
        }
        byte[] payload = new byte[(int) len];
        in.read(payload);
        return "opcode=" + opcode + " len=" + len;
    }

    static void sendTextFrame(OutputStream out, String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] mask = {(byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78};
        byte[] masked = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) masked[i] = (byte) (payload[i] ^ mask[i % 4]);
        out.write(0x81); // FIN + text
        writePayloadLen(out, payload.length, true);
        out.write(mask);
        out.write(masked);
        out.flush();
    }

    static void sendPingFrame(OutputStream out, byte[] data) throws Exception {
        byte[] mask = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};
        byte[] masked = new byte[data.length];
        for (int i = 0; i < data.length; i++) masked[i] = (byte) (data[i] ^ mask[i % 4]);
        out.write(0x89); // FIN + ping
        writePayloadLen(out, data.length, true);
        out.write(mask);
        out.write(masked);
        out.flush();
    }

    static void writePayloadLen(OutputStream out, int len, boolean masked) throws Exception {
        int maskBit = masked ? 0x80 : 0;
        if (len <= 125) {
            out.write(maskBit | len);
        } else if (len <= 65535) {
            out.write(maskBit | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        }
    }

    static void expect(String label, Object expected, Object actual) {
        boolean ok = java.util.Objects.equals(expected, actual);
        if (ok) { passed++; }
        else { System.out.println("  FAIL " + label + ": expected=" + expected + " actual=" + actual); }
    }

    static final class ByteArrayOutputStream2 extends java.io.ByteArrayOutputStream {
        String toStr(java.nio.charset.Charset cs) { return new String(super.buf, 0, super.count, cs); }
    }
}