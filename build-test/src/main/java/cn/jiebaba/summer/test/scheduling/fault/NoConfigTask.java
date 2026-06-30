package cn.jiebaba.summer.test.scheduling.fault;

import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.scheduling.Scheduled;

@Component
public class NoConfigTask {
    @Scheduled
    public void tick() {}
}
