package cn.jiebaba.summer.sample.runner;

import cn.jiebaba.summer.boot.ApplicationArguments;
import cn.jiebaba.summer.boot.ApplicationRunner;
import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.annotation.Order;

import java.util.logging.Logger;

/**
 * Sample {@link ApplicationRunner}: runs once the context is ready and the web server is
 * listening. Demonstrates cache warm-up / dictionary loading on startup. Harmless (logging only)
 * so it never breaks smoke tests that boot the sample application.
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