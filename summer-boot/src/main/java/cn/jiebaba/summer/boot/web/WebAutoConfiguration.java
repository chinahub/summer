package cn.jiebaba.summer.boot.web;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.ConditionalOnMissingBean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.web.bind.HandlerMethodArgumentResolver;
import cn.jiebaba.summer.web.cors.CorsFilter;
import cn.jiebaba.summer.web.cors.CorsProperties;
import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.multipart.MultipartFileArgumentResolver;
import cn.jiebaba.summer.web.resource.StaticResourceHandler;
import cn.jiebaba.summer.web.resource.StaticResourceProperties;
import cn.jiebaba.summer.web.sse.SseHub;

/**
 * Web 层自动配置。注册 multipart 参数解析器，使
 * {@code @RequestPart}/{@code MultipartFile} 参数能够依据解析后的
 * multipart/form-data 请求体进行解析；并注册 CORS 过滤器，使跨域请求
 * 在路由分派之前得到处理。各 Bean 均带 {@code @ConditionalOnMissingBean}
 * 退避保护，允许应用定义同类型 Bean 替换默认实现。只要 classpath 上存在
 * summer-boot 即生效。
 */
@Configuration
public class WebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MultipartFileArgumentResolver.class)
    public HandlerMethodArgumentResolver multipartFileArgumentResolver(Environment env) {
        return new MultipartFileArgumentResolver(env);
    }

    /** 绑定 summer.web.cors.* 配置项为 CorsProperties。 */
    @Bean
    @ConditionalOnMissingBean
    public CorsProperties corsProperties(Environment env) {
        return CorsProperties.from(env);
    }

    /** 注册 CORS 过滤器（summer.web.cors.enabled=false 时为透传空操作）。 */
    @Bean
    @ConditionalOnMissingBean(CorsFilter.class)
    public Filter corsFilter(CorsProperties corsProperties) {
        return new CorsFilter(corsProperties);
    }

    /**
     * 注册 SSE 广播中枢：一对多推送原语（自动事件 id + 断线补发 + 心跳保活），
     * 应用可直接注入使用；应用自定义 SseHub Bean 时退避。容器关闭时自动 close。
     */
    @Bean
    @ConditionalOnMissingBean
    public SseHub sseHub() {
        return new SseHub();
    }

    /** 绑定 summer.web.static.* 配置项为 StaticResourceProperties。 */
    @Bean
    @ConditionalOnMissingBean
    public StaticResourceProperties staticResourceProperties(Environment env) {
        return StaticResourceProperties.from(env);
    }

    /**
     * 注册静态资源处理器：路由未命中的 GET 回退静态资源（控制台前端随 jar 一体化交付）；
     * summer.web.static.enabled=false 或应用自定义 Bean 时退避/关闭。
     */
    @Bean
    @ConditionalOnMissingBean
    public StaticResourceHandler staticResourceHandler(StaticResourceProperties properties) {
        return new StaticResourceHandler(properties);
    }
}
