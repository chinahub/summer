package cn.jiebaba.summer.test.scheduling.fault;

import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.scheduling.Scheduled;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GoodTask {
    public final AtomicInteger runs = new AtomicInteger();

    @Scheduled(fixedDelay = 20, initialDelay = 0)
    public void tick() {
        runs.incrementAndGet();
    }
}
