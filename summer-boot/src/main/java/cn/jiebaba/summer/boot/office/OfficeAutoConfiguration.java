package cn.jiebaba.summer.boot.office;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.office.Office;

/**
 * summer-office 自动配置：将 {@link Office} 门面注册为 Bean，供使用方注入后按格式创建读写器。
 * <p>本类位于 summer-boot，编译期引用 summer-office（optional）；运行期由
 * {@code SummerApplication} 在探测到 summer-office 在 classpath 后才注册，
 * summer-office 不在时本类永不被加载，不会触发 NoClassDefFoundError。
 */
@Configuration
public class OfficeAutoConfiguration {

    @Bean
    public Office office() {
        return Office.create();
    }
}
