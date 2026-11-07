package cn.jiebaba.summer.sample.task;

import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.scheduling.Scheduled;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@Component
public class HeartbeatTask {
    private static final Logger LOG = Logger.getLogger(HeartbeatTask.class.getName());
    private final AtomicInteger beats = new AtomicInteger(0);

    @Scheduled(fixedDelay = 60000, initialDelay = 5000)
    public void beat() {
        LOG.info("heartbeat #" + beats.incrementAndGet());
    }
}
