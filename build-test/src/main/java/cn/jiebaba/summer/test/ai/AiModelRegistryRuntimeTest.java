package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.ChatModel;
import cn.jiebaba.summer.boot.ai.AiModelRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link AiModelRegistry} 运行期注册/注销与多级回落（resolve）的单测：
 * 服务"数字员工实例绑定模型、未绑定回落角色默认"的场景。
 */
public class AiModelRegistryRuntimeTest {

    private static ChatModel model() {
        return new StubChatModel();
    }

    @Test
    @DisplayName("运行期注册与覆盖：立即对 get/getOptional 生效")
    void registerAndOverride() {
        AiModelRegistry registry = new AiModelRegistry(new LinkedHashMap<>());
        ChatModel m1 = model();
        ChatModel m2 = model();
        registry.register("lin", m1);
        Assertions.assertSame(m1, registry.get("lin"));
        Assertions.assertSame(m1, registry.getOptional("lin"));
        registry.register("lin", m2);
        Assertions.assertSame(m2, registry.get("lin"), "同 id 再注册应覆盖");
        Assertions.assertEquals(java.util.Set.of("lin"), registry.ids());
    }

    @Test
    @DisplayName("注销：移除实例并返回是否命中")
    void unregisterRemoves() {
        AiModelRegistry registry = new AiModelRegistry(new LinkedHashMap<>());
        registry.register("lin", model());
        Assertions.assertTrue(registry.unregister("lin"));
        Assertions.assertFalse(registry.unregister("lin"));
        Assertions.assertNull(registry.getOptional("lin"));
        Assertions.assertTrue(registry.isEmpty());
    }

    @Test
    @DisplayName("resolve 多级回落：实例 → 角色默认")
    void resolveFallsBack() {
        AiModelRegistry registry = new AiModelRegistry(new LinkedHashMap<>());
        ChatModel roleDefault = model();
        registry.register("developer", roleDefault);
        // 实例未绑定 → 回落角色默认
        Assertions.assertSame(roleDefault, registry.resolve("developer-lin", "developer"));
        // 实例已绑定 → 优先实例
        ChatModel instance = model();
        registry.register("developer-lin", instance);
        Assertions.assertSame(instance, registry.resolve("developer-lin", "developer"));
    }

    @Test
    @DisplayName("resolve 全部未命中：异常列出已尝试 id 与已注册实例")
    void resolveFailureListsTriedIds() {
        AiModelRegistry registry = new AiModelRegistry(new LinkedHashMap<>());
        registry.register("developer", model());
        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> registry.resolve("lin", "su"));
        Assertions.assertTrue(e.getMessage().contains("lin") && e.getMessage().contains("su"),
                "应列出已尝试 id：" + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("developer"),
                "应列出已注册实例：" + e.getMessage());
    }

    @Test
    @DisplayName("resolve 跳过 null id（实例未设置绑定场景）")
    void resolveSkipsNullIds() {
        ChatModel fallback = model();
        AiModelRegistry registry = new AiModelRegistry(Map.of("developer", fallback));
        Assertions.assertSame(fallback, registry.resolve(null, "developer"));
    }

    @Test
    @DisplayName("非法注册被拒绝")
    void rejectIllegalRegistration() {
        AiModelRegistry registry = new AiModelRegistry(new LinkedHashMap<>());
        Assertions.assertThrows(IllegalArgumentException.class, () -> registry.register(null, model()));
        Assertions.assertThrows(IllegalArgumentException.class, () -> registry.register("x", null));
    }
}
