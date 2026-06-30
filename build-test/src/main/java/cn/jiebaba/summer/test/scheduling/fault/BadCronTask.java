package cn.jiebaba.summer.test.scheduling.fault;

import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.scheduling.Scheduled;

@Component
public class BadCronTask {
    @Scheduled(cron = "0 0 31 2 *")
    public void tick() {}
}
