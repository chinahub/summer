package cn.jiebaba.summer.sample.runner;

import cn.jiebaba.summer.boot.ApplicationArguments;
import cn.jiebaba.summer.boot.ApplicationRunner;
import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.annotation.Order;

import java.util.logging.Logger;

/**
 * 示例 {@link ApplicationRunner}：在上下文就绪、Web 服务器开始监听后运行一次。
 * 演示启动时的缓存预热 / 字典加载。该实现只输出日志、无副作用，
 * 因此不会破坏启动示例应用的冒烟测试。
 */
@Component
@Order(1)
public class StartupRunner implements ApplicationRunner {

    private static final Logger LOG = Logger.getLogger(StartupRunner.class.getName());

    @Override
    public void run(ApplicationArguments args) {
        LOG.info("ApplicationRunner: application ready, warming up caches / loading dictionaries...");
        if (args.containsOption("verbose")) {
            LOG.info("ApplicationRunner: verbose mode, non-option args=" + args.getNonOptionArgs());
        }
    }
}
