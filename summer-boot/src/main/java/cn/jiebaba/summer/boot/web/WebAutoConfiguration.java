package cn.jiebaba.summer.boot.web;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.web.bind.HandlerMethodArgumentResolver;
import cn.jiebaba.summer.web.multipart.MultipartFileArgumentResolver;

/**
 * summer-web 的自动配置。注册 multipart 参数解析器，使
 * {@code @RequestPart}/{@code MultipartFile} 参数能够依据解析后的
 * multipart/form-data 请求体进行解析。只要 classpath 上存在 summer-boot 即生效。
 */
@Configuration
public class WebAutoConfiguration {

    @Bean
    public HandlerMethodArgumentResolver multipartFileArgumentResolver(Environment env) {
        return new MultipartFileArgumentResolver(env);
    }
}
