package cn.jiebaba.summer.boot.web;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.web.bind.HandlerMethodArgumentResolver;
import cn.jiebaba.summer.web.cors.CorsFilter;
import cn.jiebaba.summer.web.cors.CorsProperties;
import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.multipart.MultipartFileArgumentResolver;

/**
 * summer-web 的自动配置。注册 multipart 参数解析器，使
 * {@code @RequestPart}/{@code MultipartFile} 参数能够依据解析后的
 * multipart/form-data 请求体进行解析；并注册 CORS 过滤器，使跨域请求
 * 在路由分派之前得到处理。只要 classpath 上存在 summer-boot 即生效。
 */
@Configuration
public class WebAutoConfiguration {

    @Bean
    public HandlerMethodArgumentResolver multipartFileArgumentResolver(Environment env) {
        return new MultipartFileArgumentResolver(env);
    }

    /** 绑定 summer.web.cors.* 配置项为 CorsProperties。 */
    @Bean
    public CorsProperties corsProperties(Environment env) {
        return CorsProperties.from(env);
    }

    /** 注册 CORS 过滤器（summer.web.cors.enabled=false 时为透传空操作）。 */
    @Bean
    public Filter corsFilter(CorsProperties corsProperties) {
        return new CorsFilter(corsProperties);
    }
}
