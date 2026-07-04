package cn.jiebaba.summer.boot.web;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.web.bind.HandlerMethodArgumentResolver;
import cn.jiebaba.summer.web.multipart.MultipartFileArgumentResolver;

/**
 * Auto-configuration for summer-web. Registers the multipart argument resolver so
 * {@code @RequestPart}/{@code MultipartFile} parameters are resolved against the
 * parsed multipart/form-data body. Active whenever summer-boot is on the classpath.
 */
@Configuration
public class WebAutoConfiguration {

    @Bean
    public HandlerMethodArgumentResolver multipartFileArgumentResolver(Environment env) {
        return new MultipartFileArgumentResolver(env);
    }
}