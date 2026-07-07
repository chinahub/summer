package cn.jiebaba.summer.web.server;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * 以阻塞 {@link SSLSocket} 为底层传输的 {@link ByteChannel} 适配器。
 * 将 SSLSocket 的输入/输出流包装为 NIO 通道，使上层 HTTP 解析与响应写入
 * 可透明地在 TLS 连接上工作（与原始 SocketChannel 互换）。
 */
public final class SslByteChannel implements ByteChannel {

    private final SSLSocket socket;
    private final ReadableByteChannel in;
    private final WritableByteChannel out;

    /** 用已完成握手的 SSLSocket 构造通道，内部以流适配器桥接读写。 */
    public SslByteChannel(SSLSocket socket) throws IOException {
        this.socket = socket;
        this.in = Channels.newChannel(socket.getInputStream());
        this.out = Channels.newChannel(socket.getOutputStream());
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return in.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return out.write(src);
    }

    @Override
    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
