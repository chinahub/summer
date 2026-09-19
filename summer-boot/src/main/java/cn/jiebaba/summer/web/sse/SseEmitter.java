package cn.jiebaba.summer.web.sse;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * SSE 异步推送发射器：handler 方法返回本实例后，响应由框架接管——请求线程（连接所属
 * 虚拟线程）持续从内部队列取事件写为 chunked SSE 帧；任意线程调用 {@link #send(Object)}
 * 推送数据（混合编码：SseEvent 完整帧 / String 原样 data / 其他对象 JSON data）。
 * <p>生命周期：{@link #complete()} 正常结束、{@link #completeWithError(Throwable)}
 * 异常结束；{@link #timeoutMillis} 大于 0 时队列空闲超过该时长触发超时并结束流。
 * 回调（onCompletion/onTimeout/onError）由框架在对应时机触发。
 */
public final class SseEmitter {

    /** 正常完成哨兵（框架内部消费 API，一般应用无需使用）。 */
    public static final Object COMPLETE = new Object();

    /** 异常完成载荷（框架内部消费 API，一般应用无需使用）。 */
    public record Failure(Throwable error) {}

    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final long timeoutMillis;
    private volatile boolean done = false;
    private Runnable onCompletion;
    private Runnable onTimeout;
    private Consumer<Throwable> onError;

    /** 无限等待（无空闲超时）。 */
    public SseEmitter() {
        this(0);
    }

    /**
     * @param timeoutMillis 队列空闲超时（毫秒）；0 表示无限等待
     */
    public SseEmitter(long timeoutMillis) {
        this.timeoutMillis = Math.max(0, timeoutMillis);
    }

    /**
     * 推送一条事件：SseEvent 按完整帧发送；String 作为 data（多行拆分）；
     * 其他对象经 JSON 序列化为 data。完成后调用抛 {@link IllegalStateException}。
     */
    public void send(Object data) {
        if (done) throw new IllegalStateException("SseEmitter already completed");
        queue.offer(Objects.requireNonNull(data, "data"));
    }

    /** 正常结束事件流：后续 send 拒绝，消费侧收到流结束。 */
    public void complete() {
        if (!done) {
            done = true;
            queue.offer(COMPLETE);
        }
    }

    /** 异常结束事件流：消费侧触发 onError 回调后结束。 */
    public void completeWithError(Throwable error) {
        if (!done) {
            done = true;
            queue.offer(new Failure(Objects.requireNonNull(error, "error")));
        }
    }

    /** 完成回调（正常或异常或超时结束后触发一次）。 */
    public void onCompletion(Runnable callback) {
        this.onCompletion = callback;
    }

    /** 超时回调（空闲超时结束时触发）。 */
    public void onTimeout(Runnable callback) {
        this.onTimeout = callback;
    }

    /** 异常回调（completeWithError 时触发）。 */
    public void onError(Consumer<Throwable> callback) {
        this.onError = callback;
    }

    public boolean isDone() {
        return done;
    }

    /** 队列空闲超时（毫秒），0 表示无限等待。 */
    public long timeoutMillis() {
        return timeoutMillis;
    }

    /**
     * 消费侧取下一条目：COMPLETE 哨兵 / Failure 载荷 / 用户数据；空闲超时返回 null。
     * 由框架的 RequestDispatcher 在请求线程上调用，一般应用无需使用。
     *
     * @throws InterruptedException 等待期间线程被中断
     */
    public Object take() throws InterruptedException {
        if (timeoutMillis > 0) {
            return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        }
        return queue.take();
    }

    /** 触发异常回调（框架内部消费 API）。 */
    public void fireError(Throwable error) {
        if (onError != null) onError.accept(error);
    }

    /** 触发超时回调（框架内部消费 API）。 */
    public void fireTimeout() {
        if (onTimeout != null) onTimeout.run();
    }

    /** 触发完成回调（框架内部消费 API）。 */
    public void fireCompletion() {
        if (onCompletion != null) onCompletion.run();
    }
}
