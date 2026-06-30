package cn.jiebaba.summer.test.scheduling.delay;

import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.scheduling.Scheduled;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FixedDelayOverlapTask {
    public final AtomicInteger runs = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();
    public volatile int maxConcurrent = 0;

    @Scheduled(fixedDelay = 50, initialDelay = 0)
    public void tick() throws InterruptedException {
        int a = active.incrementAndGet();
        if (a > maxConcurrent) maxConcurrent = a;
        runs.incrementAndGet();
        Thread.sleep(120);
        active.decrementAndGet();
    }
}
