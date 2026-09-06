package cn.jiebaba.summer.test.boot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

/** summer.factories SPI 自动配置加载的解析测试（反射调用私有加载器 + 测试资源文件）。 */
public class SpiFactoriesTest {

    @SuppressWarnings("unchecked")
    private static List<Class<?>> loadSpi() throws Exception {
        Method m = Class.forName("cn.jiebaba.summer.boot.SummerApplication")
                .getDeclaredMethod("loadSpiConfigurations");
        m.setAccessible(true);
        return (List<Class<?>>) m.invoke(null);
    }

    @Test
    public void factoriesResourceParsedAndMissingClassesSkipped() throws Exception {
        List<Class<?>> configs = loadSpi();
        Assertions.assertTrue(configs.contains(TestSpiConfig.class),
                "summer.factories 中的 @Configuration 类应被注册，实际: " + configs);
        Assertions.assertFalse(configs.contains(String.class), "非法类名应被跳过");
    }
}
