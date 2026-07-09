package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.Message;
import cn.jiebaba.summer.ai.chat.Prompt;
import cn.jiebaba.summer.ai.document.Document;
import cn.jiebaba.summer.ai.rag.RagClient;
import cn.jiebaba.summer.ai.rag.RetrievalAugmentationAdvisor;
import cn.jiebaba.summer.ai.rag.VectorStoreRetriever;
import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.vectorstore.InMemoryVectorStore;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

import java.util.List;

/** RAG 检索增强的单元测试。 */
public class RagTest {

    private InMemoryVectorStore newStore() {
        InMemoryVectorStore store = new InMemoryVectorStore(new StubEmbeddingModel());
        store.add(List.of(
                Document.of("apple banana fruit"),
                Document.of("cat dog pet"),
                Document.of("java spring framework")));
        return store;
    }

    @Test
    public void retrieveContextReturnsRelevantDoc() {
        RetrievalAugmentationAdvisor advisor = new RetrievalAugmentationAdvisor(
                new VectorStoreRetriever(newStore(), 3));
        String context = advisor.retrieveContext("apple");
        Assert.assertTrue(context.contains("apple"), "上下文应包含相关文档，实际: " + context);
    }

    @Test
    public void augmentInjectsContextBeforeUser() {
        RetrievalAugmentationAdvisor advisor = new RetrievalAugmentationAdvisor(
                new VectorStoreRetriever(newStore(), 3));
        Prompt original = new Prompt(List.of(
                Message.system("你是助手"),
                Message.user("apple 是什么")));
        Prompt augmented = advisor.augment(original);
        Assert.assertTrue(augmented.getMessages().size() > original.getMessages().size(), "应注入上下文消息");
        int userIdx = -1;
        int ctxIdx = -1;
        for (int i = 0; i < augmented.getMessages().size(); i++) {
            if ("user".equals(augmented.getMessages().get(i).role())) userIdx = i;
            if (augmented.getMessages().get(i).content() != null
                    && augmented.getMessages().get(i).content().contains("参考资料")) ctxIdx = i;
        }
        Assert.assertTrue(ctxIdx >= 0, "应存在参考资料 system 消息");
        Assert.assertTrue(ctxIdx < userIdx, "参考资料应在用户提问之前");
    }

    @Test
    public void ragClientEndToEnd() {
        StubChatModel stub = new StubChatModel(new ChatResponse("苹果是一种水果", null, "stop", null));
        RetrievalAugmentationAdvisor advisor = new RetrievalAugmentationAdvisor(
                new VectorStoreRetriever(newStore(), 3));
        RagClient client = new RagClient(ChatClient.create(stub), advisor);
        ChatResponse resp = client.ask("你是助手", "apple");
        Assert.assertEquals("苹果是一种水果", resp.content());
        Assert.assertNotNull(stub.lastPrompt(), "应已调用底层模型");
        boolean hasContext = false;
        for (Message m : stub.lastPrompt().getMessages()) {
            if (m.content() != null && m.content().contains("apple")) {
                hasContext = true;
                break;
            }
        }
        Assert.assertTrue(hasContext, "发送给模型的提示应包含检索到的参考资料");
    }
}
