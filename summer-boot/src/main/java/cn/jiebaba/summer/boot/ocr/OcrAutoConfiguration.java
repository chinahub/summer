package cn.jiebaba.summer.boot.ocr;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.annotation.Lazy;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.office.ocr.Ocr;

/**
 * summer-office OCR 自动配置：按 summer.ocr.* 装配 {@link Ocr}。
 * <p>本类位于 summer-boot，编译期引用 summer-office（optional）；运行期由 SummerApplication
 * 在探测到 summer-office 的 OCR 类在 classpath 后才注册加载。{@link Ocr} 以 {@code @Lazy} 注册，
 * 仅在注入时才初始化原生引擎与模型，未配置且未被注入时不会影响启动。
 */
@Configuration
public class OcrAutoConfiguration {

    @Bean
    public OcrProperties ocrProperties(Environment env) {
        return OcrProperties.from(env);
    }

    /** 装配 OCR 引擎（懒加载）；未配置必要项时注入会抛出明确异常。 */
    @Bean
    @Lazy
    public Ocr ocr(OcrProperties properties) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "summer-office OCR 已在 classpath 但未配置：请设置 summer.ocr.lib-path"
                            + " / summer.ocr.det-model-path / summer.ocr.rec-model-path"
                            + "（dict-path 在使用内嵌字典的 PP-OCRv4/v6 模型时可选）");
        }
        return Ocr.create(properties.toConfig());
    }
}
