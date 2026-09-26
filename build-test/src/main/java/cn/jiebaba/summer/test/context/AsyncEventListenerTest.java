package cn.jiebaba.summer.test.context;

import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.annotation.EventListener;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 异步事件监听（{@code @EventListener(async = true)}）单测：
 * 异步监听器在独立虚拟线程执行、异常隔离不向发布方传播；同步默认语义不变。
 */
public class AsyncEventListenerTest {

    static class AsyncEvent {}
    static class SyncThrowEvent {}

    @Configuration
    static class AsyncConfig {
        final CountDownLatch asyncLatch = new CountDownLatch(1);
        final CountDownLatch boomLatch = new CountDownLatch(1);
        volatile Thread asyncThread;
        volatile String received;

        @EventListener(async = true)
        public void onAsync(AsyncEvent event) {
            asyncThread = Thread.currentThread();
            received = "ok";
            asyncLatch.countDown();
        }

        @EventListener(async = true)
        public void onAsyncBoom(AsyncEvent event) {
            boomLatch.countDown();
            throw new IllegalStateException("异步监听器故意失败");
        }

        @EventListener
        public void onSyncThrow(SyncThrowEvent event) {
            throw new IllegalStateException("同步监听器失败应向发布方传播");
        }
    }

    private static DefaultApplicationContext newContext() {
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, new Environment(), Set.of());
        ctx.registerBeanDefinition("asyncConfig", new BeanDefinition("asyncConfig", AsyncConfig.class));
        ctx.refresh();
        return ctx;
    }

    @Test
    public void asyncListenerRunsOffThreadAndSurvivesSiblingFailure() throws Exception {
        DefaultApplicationContext ctx = newContext();
        try {
            AsyncConfig cfg = (AsyncConfig) ctx.getBean("asyncConfig");
            // 一个异步监听器抛异常不应影响发布方，也不应阻断兄弟监听器
            Assertions.assertDoesNotThrow(() -> ctx.publishEvent(new AsyncEvent()));
            Assertions.assertTrue(cfg.asyncLatch.await(3, TimeUnit.SECONDS), "异步监听器应被执行");
            Assertions.assertTrue(cfg.boomLatch.await(3, TimeUnit.SECONDS), "抛异常的异步监听器也应被执行");
            Assertions.assertEquals("ok", cfg.received);
            Assertions.assertNotSame(Thread.currentThread(), cfg.asyncThread,
                    "异步监听器应在独立线程执行");
        } finally {
            ctx.close();
        }
    }

    @Test
    public void syncListenerStillPropagatesException() {
        DefaultApplicationContext ctx = newContext();
        try {
            RuntimeException e = Assertions.assertThrows(RuntimeException.class,
                    () -> ctx.publishEvent(new SyncThrowEvent()));
            StringBuilder messages = new StringBuilder();
            for (Throwable t = e; t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            Assertions.assertTrue(messages.toString().contains("同步监听器"),
                    "同步监听器异常应向发布方传播，实际: " + messages);
        } finally {
            ctx.close();
        }
    }
}
