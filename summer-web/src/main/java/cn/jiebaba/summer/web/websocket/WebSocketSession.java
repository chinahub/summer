package cn.jiebaba.summer.web.websocket;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 表示单个 WebSocket 连接。握手完成后，{@link #runLoop()} 阻塞于 socket 读取帧并
 * 分派到端点回调。设计为运行在虚拟线程上（阻塞式 IO）。
 */
public final class WebSocketSession {

    private static final Logger LOG = Logger.getLogger(WebSocketSession.class.getName());

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final WebSocketEndpointInfo endpoint;
    private final String id;
    private volatile boolean closed = false;
    // 分片消息缓冲区
    private final ByteArrayOutputStream fragmentBuffer = new ByteArrayOutputStream();
    private int currentFragmentOpcode = -1;

    public WebSocketSession(Socket socket, WebSocketEndpointInfo endpoint) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.endpoint = endpoint;
        this.id = Integer.toHexString(System.identityHashCode(this));
    }

    public String id() { return id; }

    /** 向客户端发送文本消息。 */
    public void sendText(String message) {
        sendFrame(0x01, message.getBytes(StandardCharsets.UTF_8));
    }

    /** 向客户端发送二进制消息。 */
    public void sendBinary(byte[] data) {
        sendFrame(0x02, data);
    }

    /** 发送 ping 帧。 */
    public void sendPing(byte[] data) {
        sendFrame(0x09, data);
    }

    /** 以给定原因优雅关闭连接。 */
    public void close(CloseReason reason) {
        if (closed) return;
        byte[] payload = new byte[2 + reason.reason().length()];
        payload[0] = (byte) ((reason.code() >> 8) & 0xFF);
        payload[1] = (byte) (reason.code() & 0xFF);
        System.arraycopy(reason.reason().getBytes(StandardCharsets.UTF_8), 0, payload, 2, reason.reason().length());
        try {
            sendFrame(0x08, payload);
        } finally {
            closeSocket();
        }
    }

    public boolean isClosed() { return closed; }

    /**
     * 主阻塞循环：读取帧并分派到端点回调；连接关闭（对端或本地关闭）时返回。
     */
    public void runLoop() {
        invokeOnOpen();
        try {
            while (!closed) {
                Frame frame = readFrame();
                if (frame == null) break;
            // 处理分片消息（RFC 6455 §5.4）
            Frame processedFrame = frame;
            if (!frame.fin()) {
                if (frame.opcode() == 0x00) {
                    // 续帧：追加到缓冲区
                    fragmentBuffer.write(frame.payload());
                    continue;
                } else {
                    // 分片消息的首帧：重置缓冲区
                    fragmentBuffer.reset();
                    currentFragmentOpcode = frame.opcode();
                    fragmentBuffer.write(frame.payload());
                    continue;
                }
            } else {
                if (frame.opcode() == 0x00 && currentFragmentOpcode != -1) {
                    // 末片：合并所有部分
                    fragmentBuffer.write(frame.payload());
                    byte[] fullPayload = fragmentBuffer.toByteArray();
                    processedFrame = new Frame(true, currentFragmentOpcode, fullPayload);
                    fragmentBuffer.reset();
                    currentFragmentOpcode = -1;
                }
            }
            processFrame(processedFrame);
            }
        } catch (IOException e) {
            if (!closed) {
                LOG.log(Level.FINE, "WebSocket IO error on session " + id, e);
                invokeOnError(e);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "WebSocket error on session " + id, e);
            invokeOnError(e);
        } finally {
            closeSocket();
            invokeOnClose();
        }
    }

    private void processFrame(Frame frame) {
        switch (frame.opcode) {
            case 0x01 -> handleTextMessage(new String(frame.payload, StandardCharsets.UTF_8));
            case 0x02 -> handleBinaryMessage(frame.payload);
            case 0x08 -> handleCloseFrame(frame.payload);
            case 0x09 -> sendFrame(0x0A, frame.payload); // 收到 ping 回复 pong
            case 0x0A -> { /* 收到 pong，忽略 */ }
            default -> { /* 未知 opcode，忽略 */ }
        }
    }

    private void handleTextMessage(String message) {
        Method m = endpoint.onMessage();
        if (m == null) return;
        invokeEndpoint(m, resolveArgs(m, message, null));
    }

    private void handleBinaryMessage(byte[] data) {
        Method m = endpoint.onMessage();
        if (m == null) return;
        invokeEndpoint(m, resolveArgs(m, null, data));
    }

    private void handleCloseFrame(byte[] payload) {
        int code = 1000;
        String reason = "";
        if (payload.length >= 2) {
            code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
            if (payload.length > 2) {
                reason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
            }
        }
        closeSocket();
        // 即将关闭，无需回显 close 帧
    }

    private Object[] resolveArgs(Method m, String text, byte[] binary) {
        Class<?>[] params = m.getParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (params[i] == WebSocketSession.class) args[i] = this;
            else if (text != null && params[i] == String.class) args[i] = text;
            else if (binary != null && params[i] == byte[].class) args[i] = binary;
            else if (text != null && params[i] == ByteBuffer.class) args[i] = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        }
        return args;
    }

    private void invokeOnOpen() {
        Method m = endpoint.onOpen();
        if (m == null) return;
        invokeEndpoint(m, resolveArgs(m, null, null));
    }

    private void invokeOnClose() {
        Method m = endpoint.onClose();
        if (m == null) return;
        invokeEndpoint(m, resolveArgs(m, null, null));
    }

    private void invokeOnError(Throwable t) {
        Method m = endpoint.onError();
        if (m == null) return;
        Class<?>[] params = m.getParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (params[i] == WebSocketSession.class) args[i] = this;
            else if (Throwable.class.isAssignableFrom(params[i])) args[i] = t;
        }
        invokeEndpoint(m, args);
    }

    private void invokeEndpoint(Method m, Object[] args) {
        try {
            m.setAccessible(true);
            m.invoke(endpoint.bean(), args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            LOG.log(Level.WARNING, "Endpoint method " + m + " threw", cause);
            invokeOnError(cause);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to invoke endpoint method " + m, e);
        }
    }

    /** 向客户端发送单个帧（服务端帧不带掩码）。 */
    private synchronized void sendFrame(int opcode, byte[] payload) {
        if (closed) return;
        try {
            ByteBuffer buf = encodeFrame(opcode, payload);
            out.write(buf.array(), 0, buf.limit());
            out.flush();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Failed to send frame on session " + id, e);
            closeSocket();
        }
    }

    /**
     * 将一个帧编码为字节缓冲：写入 FIN+opcode 与长度字段（按 7/16/64 位三种情况），
     * 再追加负载。服务端帧不带掩码。
     *
     * @param opcode  帧操作码
     * @param payload 帧负载
     * @return 编码后的字节缓冲
     */
    private static ByteBuffer encodeFrame(int opcode, byte[] payload) {
        int headerLen = 2;
        if (payload.length <= 125) {
            headerLen = 2;
        } else if (payload.length <= 65535) {
            headerLen = 4;
        } else {
            headerLen = 10;
        }
        ByteBuffer buf = ByteBuffer.allocate(headerLen + payload.length);
        buf.put((byte) (0x80 | (opcode & 0x0F))); // FIN=1（结束帧）
        if (payload.length <= 125) {
            buf.put((byte) payload.length);
        } else if (payload.length <= 65535) {
            buf.put((byte) 126);
            buf.putShort((short) payload.length);
        } else {
            buf.put((byte) 127);
            buf.putLong(payload.length);
        }
        buf.put(payload);
        buf.flip();
        return buf;
    }

    /** 从流中读取单个 WebSocket 帧（阻塞）。 */
    private Frame readFrame() throws IOException {
        int b0 = in.read();
        if (b0 == -1) return null;
        int b1 = in.read();
        if (b1 == -1) return null;

        boolean fin = (b0 & 0x80) != 0;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;

        if (len == 126) {
            int high = in.read();
            int low = in.read();
            if (low == -1) return null;
            len = ((high & 0xFF) << 8) | (low & 0xFF);
        } else if (len == 127) {
            long ext = 0;
            for (int i = 0; i < 8; i++) {
                int b = in.read();
                if (b == -1) return null;
                ext = (ext << 8) | (b & 0xFF);
            }
            len = ext;
        }

        byte[] maskKey = null;
        if (masked) {
            maskKey = new byte[4];
            int read = 0;
            while (read < 4) {
                int r = in.read(maskKey, read, 4 - read);
                if (r == -1) return null;
                read += r;
            }
        }

        if (len < 0 || len > 16 * 1024 * 1024) {
            throw new IOException("Frame payload too large: " + len);
        }
        byte[] payload = new byte[(int) len];
        int read = 0;
        while (read < len) {
            int r = in.read(payload, read, (int) len - read);
            if (r == -1) return null;
            read += r;
        }

        if (masked && maskKey != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] ^= maskKey[i % 4];
            }
        }

        return new Frame(fin, opcode, payload);
    }

    private void closeSocket() {
        if (closed) return;
        closed = true;
        try { socket.close(); } catch (IOException ignore) {}
    }

    /** 已解析的 WebSocket 帧。 */
    record Frame(boolean fin, int opcode, byte[] payload) {}
}
