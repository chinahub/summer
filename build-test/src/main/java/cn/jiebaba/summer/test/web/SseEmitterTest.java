package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.web.sse.SseEmitter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** SseEmitter 生命周期测试：send/complete/completeWithError/超时与回调时序。 */
public class SseEmitterTest {

    @Test
    public void sendThenCompleteDeliversItemsAndSentinel() throws Exception {
        SseEmitter emitter = new SseEmitter();
        emitter.send("a");
        emitter.send("b");
        emitter.complete();
        List<Object> received = new ArrayList<>();
        received.add(emitter.take());
        received.add(emitter.take());
        received.add(emitter.take());
        Assertions.assertEquals("a", received.get(0));
        Assertions.assertEquals("b", received.get(1));
        Assertions.assertEquals(SseEmitter.COMPLETE, received.get(2));
        Assertions.assertTrue(emitter.isDone());
    }

    @Test
    public void sendAfterCompleteRejected() {
        SseEmitter emitter = new SseEmitter();
        emitter.complete();
        Assertions.assertThrows(IllegalStateException.class, () -> emitter.send("late"));
    }

    @Test
    public void completeWithErrorDeliversFailureAndFiresCallbacks() throws Exception {
        SseEmitter emitter = new SseEmitter();
        AtomicReference<Throwable> error = new AtomicReference<>();
        List<Boolean> completed = new ArrayList<>();
        emitter.onError(error::set);
        emitter.onCompletion(() -> completed.add(Boolean.TRUE));
        emitter.send("x");
        emitter.completeWithError(new IllegalStateException("boom"));
        Assertions.assertEquals("x", emitter.take());
        Object item = emitter.take();
        Assertions.assertInstanceOf(SseEmitter.Failure.class, item);
        emitter.fireError(((SseEmitter.Failure) item).error());
        Assertions.assertEquals("boom", error.get().getMessage());
        emitter.fireCompletion();
        Assertions.assertEquals(1, completed.size());
    }

    @Test
    public void idleTimeoutReturnsNullAndFiresTimeout() throws Exception {
        SseEmitter emitter = new SseEmitter(50);
        AtomicReference<Runnable> timeoutCb = new AtomicReference<>();
        emitter.onTimeout(() -> timeoutCb.set(() -> { }));
        long start = System.currentTimeMillis();
        Object item = emitter.take();
        long elapsed = System.currentTimeMillis() - start;
        Assertions.assertNull(item, "空闲超时应返回 null");
        Assertions.assertTrue(elapsed >= 40, "应等待超时时长，实际 " + elapsed + "ms");
        emitter.fireTimeout();
        Assertions.assertNotNull(timeoutCb.get());
    }

    @Test
    public void crossThreadSendWakesConsumer() throws Exception {
        SseEmitter emitter = new SseEmitter();
        CountDownLatch sent = new CountDownLatch(1);
        Thread producer = Thread.startVirtualThread(() -> {
            try {
                sent.await();
                Thread.sleep(50);
                emitter.send("async");
                emitter.complete();
            } catch (InterruptedException ignored) {
            }
        });
        sent.countDown();
        Assertions.assertEquals("async", emitter.take());
        Assertions.assertEquals(SseEmitter.COMPLETE, emitter.take());
        producer.join(TimeUnit.SECONDS.toMillis(2));
    }
}
