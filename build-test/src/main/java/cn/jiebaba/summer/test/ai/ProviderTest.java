package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.model.Provider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Provider 厂商枚举解析的单元测试：名称不区分大小写匹配、未匹配返回 null、默认 base-url 与模型名正确。 */
public class ProviderTest {

    @Test
    public void resolveKimiCaseInsensitive() {
        Assertions.assertEquals(Provider.KIMI, Provider.from("kimi"));
        Assertions.assertEquals(Provider.KIMI, Provider.from("KIMI"));
        Assertions.assertEquals(Provider.KIMI, Provider.from(" Kimi "));
    }

    @Test
    public void kimiDefaults() {
        Assertions.assertEquals("https://api.moonshot.cn/v1", Provider.KIMI.getDefaultBaseUrl());
        Assertions.assertEquals("kimi-k3", Provider.KIMI.getDefaultModel());
    }

    @Test
    public void glmDefaults() {
        Assertions.assertEquals("https://open.bigmodel.cn/api/paas/v4", Provider.GLM.getDefaultBaseUrl());
        Assertions.assertEquals("glm-5.3-flash", Provider.GLM.getDefaultModel());
    }

    @Test
    public void existingProvidersStillResolve() {
        Assertions.assertEquals(Provider.DEEPSEEK, Provider.from("deepseek"));
        Assertions.assertEquals(Provider.GLM, Provider.from("GLM"));
        Assertions.assertEquals(Provider.MINIMAX, Provider.from("minimax"));
    }

    @Test
    public void unmatchedOrNullReturnsNull() {
        Assertions.assertNull(Provider.from("openai"));
        Assertions.assertNull(Provider.from(null));
        Assertions.assertNull(Provider.from("  "));
    }
}
