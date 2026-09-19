package cn.jiebaba.summer.boot.office;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.office.Office;

/**
 * Office 自动配置：将 {@link Office} 门面注册为 Bean，供使用方注入后按格式创建读写器。
 * <p>office 代码已并入 summer-boot（同 jar），故由 {@code SummerApplication} 无条件注册，
 * 无需 classpath 探测。
 */
@Configuration
public class OfficeAutoConfiguration {

    @Bean
    public Office office() {
        return Office.create();
    }
}
