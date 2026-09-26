package cn.jiebaba.summer.web.sse;

import cn.jiebaba.summer.core.annotation.PreDestroy;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 广播中枢：一对多推送的进程内原语。应用侧 handler 把 {@link SseEmitter} 交给
 * {@link #subscribe} 登记，之后 {@link #broadcast} 一帧即可送达全部订阅者；
 * 天然适合"状态变更 → 全局通知"类场景（待办提醒、看板实时刷新）。
 * <p>三件事由中枢兜住，应用无需自建：</p>
 * <ul>
 *   <li><b>自动事件 id 与断线补发</b>：广播帧自动分配递增 id 并进入环形补发缓冲；
 *       重连请求携带 {@code Last-Event-ID}（见 {@link #subscribe(SseEmitter, String)}）
 *       时自动补发其后的帧，通知不丢；</li>
 *   <li><b>心跳保活</b>：首个订阅者出现后启动虚拟线程定时发送 {@link SseComment}
 *       注释帧（默认 15s，0 关闭），防代理/网关空闲断连；</li>
 *   <li><b>自动清理</b>：完成/断开的订阅者在广播与心跳时自动剔除。</li>
 * </ul>
 * 线程安全：任意线程可广播/订阅/退订；{@link #close()} 后广播抛
 * {@link IllegalStateException} 并完成全部订阅者。作为 Bean 时容器关闭自动调用 {@link #close()}。
 */
public final class SseHub {

    /** 默认补发缓冲容量（帧数） */
    public static final int DEFAULT_REPLAY_BUFFER = 256;

    /** 默认心跳间隔（毫秒） */
    public static final long DEFAULT_HEARTBEAT_MILLIS = 15_000;

    private final Object lock = new Object();
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
    private final ArrayDeque<SseEvent> replayBuffer = new ArrayDeque<>();
    private final int replayCapacity;
    private final long heartbeatMillis;
    private long seq = 0;
    private Thread heartbeatThread;
    private volatile boolean closed;

    /** 默认参数：补发缓冲 256 帧、心跳 15 秒。 */
    public SseHub() {
        this(DEFAULT_REPLAY_BUFFER, DEFAULT_HEARTBEAT_MILLIS);
    }

    /**
     * @param replayCapacity  补发缓冲容量（保留最近 N 帧供断线重连补发，&lt;=0 关闭补发）
     * @param heartbeatMillis 心跳间隔（毫秒，0 关闭心跳）
     */
    public SseHub(int replayCapacity, long heartbeatMillis) {
        this.replayCapacity = Math.max(0, replayCapacity);
        this.heartbeatMillis = Math.max(0, heartbeatMillis);
    }

    /**
     * 订阅事件流：登记 emitter 并返回自身（便于 handler 直接返回）。无断线补发，
     * 等价 {@code subscribe(emitter, null)}。
     */
    public SseEmitter subscribe(SseEmitter emitter) {
        return subscribe(emitter, null);
    }

    /**
     * 订阅事件流并按 {@code lastEventId} 断线补发：补发缓冲中 id 大于该值的全部帧
     * （{@code Last-Event-ID} 请求头原样传入；为空或不可解析时不补发）。
     */
    public SseEmitter subscribe(SseEmitter emitter, String lastEventId) {
        if (emitter == null) throw new IllegalArgumentException("emitter 不能为空");
        ensureOpen();
        startHeartbeat();
        Long after = parseId(lastEventId);
        synchronized (lock) {
            if (after != null && replayCapacity > 0) {
                for (SseEvent event : replayBuffer) {
                    Long id = parseId(event.id());
                    if (id != null && id > after) sendOrDrop(emitter, event);
                }
            }
            subscribers.add(emitter);
        }
        return emitter;
    }

    /** 广播一条 data 事件到全部订阅者；返回分配的事件 id。 */
    public long broadcast(String data) {
        return broadcast(SseEvent.of(data));
    }

    /** 广播一条具名事件到全部订阅者；返回分配的事件 id。 */
    public long broadcast(String data, String event) {
        return broadcast(SseEvent.of(data, event));
    }

    /**
     * 广播一帧到全部订阅者：未携带 id 时自动分配递增 id 并记入补发缓冲；
     * 已携带 id 的帧按原样发送但不进补发缓冲（由调用方自管 id 时的语义）。
     *
     * @return 分配（或原样保留）的事件 id
     */
    public long broadcast(SseEvent event) {
        if (event == null) throw new IllegalArgumentException("event 不能为空");
        ensureOpen();
        synchronized (lock) {
            long id = ++seq;
            boolean assigned = event.id() == null || event.id().isBlank();
            SseEvent frame = assigned
                    ? new SseEvent(event.data(), event.event(), String.valueOf(id), event.retry())
                    : event;
            if (assigned && replayCapacity > 0) {
                replayBuffer.addLast(frame);
                while (replayBuffer.size() > replayCapacity) replayBuffer.removeFirst();
            }
            for (SseEmitter emitter : subscribers) {
                sendOrDrop(emitter, frame);
            }
            return id;
        }
    }

    /** 退订并结束该事件流（emitter.complete()）。 */
    public void unsubscribe(SseEmitter emitter) {
        if (emitter == null) return;
        subscribers.remove(emitter);
        if (!emitter.isDone()) emitter.complete();
    }

    /** 当前订阅者数量。 */
    public int subscriberCount() {
        return subscribers.size();
    }

    /** 结束中枢：停心跳、完成全部订阅者并清空；可重复调用。 */
    @PreDestroy
    public void close() {
        synchronized (lock) {
            if (closed) return;
            closed = true;
            if (heartbeatThread != null) heartbeatThread.interrupt();
            for (SseEmitter emitter : subscribers) {
                if (!emitter.isDone()) emitter.complete();
            }
            subscribers.clear();
            replayBuffer.clear();
        }
    }

    /** 发送一帧，已完成的订阅者剔除、发送被拒（并发完成）同样剔除。 */
    private void sendOrDrop(SseEmitter emitter, Object frame) {
        if (emitter.isDone()) {
            subscribers.remove(emitter);
            return;
        }
        try {
            emitter.send(frame);
        } catch (IllegalStateException done) {
            subscribers.remove(emitter);
        }
    }

    /** 首个订阅者出现后启动心跳虚拟线程（仅一次）。 */
    private void startHeartbeat() {
        if (heartbeatMillis <= 0 || heartbeatThread != null) return;
        synchronized (lock) {
            if (heartbeatThread != null || closed) return;
            heartbeatThread = Thread.ofVirtual().name("sse-hub-heartbeat").start(this::heartbeatLoop);
        }
    }

    /** 心跳循环：定时向全部订阅者发送注释帧保活，中枢关闭后退出。 */
    private void heartbeatLoop() {
        while (!closed) {
            try {
                Thread.sleep(heartbeatMillis);
            } catch (InterruptedException e) {
                return;
            }
            SseComment hb = SseComment.heartbeat();
            synchronized (lock) {
                for (SseEmitter emitter : subscribers) {
                    sendOrDrop(emitter, hb);
                }
            }
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("SseHub 已关闭");
    }

    private static Long parseId(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
