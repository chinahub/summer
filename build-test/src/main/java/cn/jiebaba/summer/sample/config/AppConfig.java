package cn.jiebaba.summer.sample.config;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AppConfig {

    @Bean
    public AtomicInteger requestCounter() {
        return new AtomicInteger(0);
    }
}
