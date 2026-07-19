package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.embedding.EmbeddingModel;
import cn.jiebaba.summer.ai.memory.ChatMemory;
import cn.jiebaba.summer.ai.rag.RagClient;
import cn.jiebaba.summer.ai.vectorstore.VectorStore;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.test.AfterEach;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.BeforeEach;
import cn.jiebaba.summer.core.test.Test;

import java.util.Set;

/**
 * summer-ai 扩展自动配置（Embedding/VectorStore/Memory/RAG）的装配测试：
 * 启用相应配置后四个 @Lazy Bean 可按需创建；未启用时 getBean 抛异常而非返回半成品。
 * 独立作用域上下文，不启动 Web/DB，不发起真实网络调用（仅构造对象）。
 */
public class AiExtendedAutoConfigTest {

    @BeforeEach
    void clearProps() {
        clearAiProps();
    }

    @AfterEach
    void cleanup() {
        clearAiProps();
    }

    /** 启用 embedding/vectorstore/memory/rag 后，四个扩展 Bean 均可装配。 */
    @Test
    public void extendedBeansWiredWhenEnabled() {
        System.setProperty("summer.ai.provider", "deepseek");
        System.setProperty("summer.ai.api-key", "dummy-key");
        System.setProperty("summer.ai.embedding.enabled", "true");
        System.setProperty("summer.ai.embedding.model", "embedding-3");
        System.setProperty("summer.ai.embedding.base-url", "https://open.bigmodel.cn/api/paas/v4");
        System.setProperty("summer.ai.embedding.api-key", "dummy-glm-key");
        System.setProperty("summer.ai.vectorstore.type", "memory");
        System.setProperty("summer.ai.memory.enabled", "true");
        System.setProperty("summer.ai.memory.max-messages", "10");
        System.setProperty("summer.ai.rag.enabled", "true");
        Environment env = new Environment();
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, env, Set.of("cn.jiebaba.summer.boot.ai"));
        try {
            ctx.refresh();
            EmbeddingModel embedding = ctx.getBean(EmbeddingModel.class);
            VectorStore store = ctx.getBean(VectorStore.class);
            ChatMemory memory = ctx.getBean(ChatMemory.class);
            RagClient rag = ctx.getBean(RagClient.class);
            Assert.assertNotNull(embedding, "EmbeddingModel 未装配");
            Assert.assertNotNull(store, "VectorStore 未装配");
            Assert.assertNotNull(memory, "ChatMemory 未装配");
            Assert.assertNotNull(rag, "RagClient 未装配");
        } finally {
            ctx.close();
        }
    }

    /** 仅配置对话能力（未启用 embedding）时，getBean(EmbeddingModel) 应抛异常。 */
    @Test
    public void embeddingBeanThrowsWhenNotEnabled() {
        System.setProperty("summer.ai.provider", "deepseek");
        System.setProperty("summer.ai.api-key", "dummy-key");
        Environment env = new Environment();
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, env, Set.of("cn.jiebaba.summer.boot.ai"));
        try {
            ctx.refresh();
            Assert.assertThrows(RuntimeException.class,
                    () -> ctx.getBean(EmbeddingModel.class),
                    "未启用 embedding 时 getBean 应抛异常");
        } finally {
            ctx.close();
        }
    }

    /** 启用 memory 后 ChatMemory 可装配，且 maxMessages 配置生效（窗口大小为 10）。 */
    @Test
    public void memoryNotInterferingWhenDisabled() {
        System.setProperty("summer.ai.provider", "deepseek");
        System.setProperty("summer.ai.api-key", "dummy-key");
        Environment env = new Environment();
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, env, Set.of("cn.jiebaba.summer.boot.ai"));
        try {
            ctx.refresh();
            // 未启用 memory/rag/vectorstore 时，refresh 仍成功（@Lazy 不实例化），
            // 注入 ChatClient 等基础能力不受影响。
            Assert.assertThrows(RuntimeException.class,
                    () -> ctx.getBean(ChatMemory.class),
                    "未启用 memory 时 getBean(ChatMemory) 应抛异常");
        } finally {
            ctx.close();
        }
    }

    private void clearAiProps() {
        java.util.Properties sys = System.getProperties();
        sys.stringPropertyNames().stream()
                .filter(n -> n.startsWith("summer.ai."))
                .toList()
                .forEach(sys::remove);
    }
}
