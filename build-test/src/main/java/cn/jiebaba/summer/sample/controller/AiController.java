package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.ai.chat.ChatClient;
import cn.jiebaba.summer.ai.chat.ChatResponse;
import cn.jiebaba.summer.ai.document.Document;
import cn.jiebaba.summer.ai.document.TextSplitter;
import cn.jiebaba.summer.ai.document.TokenTextSplitter;
import cn.jiebaba.summer.ai.memory.ChatMemory;
import cn.jiebaba.summer.ai.memory.MemoryChatClient;
import cn.jiebaba.summer.ai.rag.RagClient;
import cn.jiebaba.summer.ai.vectorstore.VectorStore;
import cn.jiebaba.summer.boot.ai.document.OfficeDocumentReader;
import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.RequestPart;
import cn.jiebaba.summer.web.annotation.RestController;
import cn.jiebaba.summer.web.multipart.MultipartFile;
import cn.jiebaba.summer.web.sse.SseEmitter;
import cn.jiebaba.summer.web.sse.SseEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * summer-ai 能力演示：同步对话、流式对话（SSE 转发）、对话记忆、RAG 检索增强与文档入库。
 * ChatClient 由 summer-boot 按 summer.ai.* 自动装配；记忆/RAG/向量库为可选装配（@Lazy），
 * 未启用时对应端点返回提示而非抛错，便于在仅配置对话能力时也能运行本示例。
 */
@RestController
@RequestMapping("/ai")
public class AiController {

    private static final String SYSTEM = "你是一名简洁的中文助手";

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ApplicationContext context;

    private final TextSplitter splitter = new TokenTextSplitter();

    /** 同步对话：返回模型回复、思维链与 token 用量。 */
    @GetMapping("/chat")
    public Map<String, Object> chat(@RequestParam("q") String question) {
        ChatResponse resp = chatClient.prompt(SYSTEM).user(question).call();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", resp.content());
        out.put("reasoning", resp.reasoningContent());
        out.put("finishReason", resp.finishReason());
        if (resp.metadata() != null) {
            out.put("tokens", resp.metadata().totalTokens());
        }
        return out;
    }

    /**
     * 流式对话（SSE）：handler 返回 SseEmitter 后立即返回，由后台虚拟线程逐 token 消费
     * 模型流推送 data 帧，末尾发送 {@code event: done} 帧后结束流。浏览器 EventSource
     * 或 curl 均可接收。需 summer.ai.* 配置完整。
     */
    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam("q") String question) {
        SseEmitter emitter = new SseEmitter(0);
        Thread.startVirtualThread(() -> {
            try (java.util.stream.Stream<ChatResponse> s = chatClient.prompt(SYSTEM).user(question).stream()) {
                s.forEach(chunk -> {
                    if (chunk.content() != null && !chunk.content().isEmpty()) {
                        emitter.send(chunk.content());
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
                return;
            }
            emitter.send(SseEvent.of("[DONE]", "done"));
            emitter.complete();
        });
        return emitter;
    }

    /** 带记忆的多轮对话：按 conv 维护会话历史。需 summer.ai.memory.enabled=true。 */
    @GetMapping("/remember")
    public Map<String, Object> remember(@RequestParam("q") String question,
                                        @RequestParam(value = "conv", defaultValue = "default") String conv) {
        ChatMemory memory = optional(ChatMemory.class);
        if (memory == null) {
            return Map.of("error", "对话记忆未启用：请设置 summer.ai.memory.enabled=true");
        }
        MemoryChatClient client = new MemoryChatClient(chatClient, memory, conv);
        ChatResponse resp = client.call(question);
        return Map.of("content", resp.content(), "conv", conv);
    }

    /** RAG 检索增强问答：先检索相关资料再增强提问。需 summer.ai.rag.enabled=true 及向量库与向量化配置。 */
    @GetMapping("/rag")
    public Map<String, Object> rag(@RequestParam("q") String question) {
        RagClient rag = optional(RagClient.class);
        if (rag == null) {
            return Map.of("error", "RAG 未启用：请设置 summer.ai.rag.enabled=true 并配置 vectorstore/embedding");
        }
        ChatResponse resp = rag.ask(SYSTEM, question);
        return Map.of("content", resp.content());
    }

    /** 上传文档入库（PDF/DOCX/Markdown/纯文本），切分后写入向量库供 RAG 检索。需 summer.ai.vectorstore.type=memory。 */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestPart("file") MultipartFile file) {
        VectorStore store = optional(VectorStore.class);
        if (store == null) {
            return Map.of("error", "向量库未启用：请设置 summer.ai.vectorstore.type=memory");
        }
        String name = file.getOriginalFilename();
        List<Document> chunks = new ArrayList<>();
        for (Document d : readDocuments(file, name)) {
            chunks.addAll(splitter.split(d));
        }
        List<String> ids = store.add(chunks);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("filename", name);
        out.put("chunks", chunks.size());
        out.put("ids", ids);
        return out;
    }

    /** 按文件扩展名选择读取器：pdf/docx/md 走 OfficeDocumentReader，其余按 UTF-8 纯文本兜底。 */
    private List<Document> readDocuments(MultipartFile file, String name) {
        String lower = name == null ? "" : name.toLowerCase();
        byte[] bytes = file.getBytes();
        try {
            if (lower.endsWith(".pdf")) {
                return OfficeDocumentReader.pdf(bytes, name).get();
            }
            if (lower.endsWith(".docx")) {
                return OfficeDocumentReader.docx(bytes, name).get();
            }
            if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
                return OfficeDocumentReader.markdown(bytes, name).get();
            }
        } catch (Exception e) {
            throw new IllegalStateException("文档解析失败: " + name, e);
        }
        return List.of(Document.builder()
                .content(new String(bytes, StandardCharsets.UTF_8))
                .metadata("source", name)
                .build());
    }

    /** 按类型查找可选 Bean（@Lazy 未启用时创建会抛异常），未装配或未启用时返回 null。 */
    private <T> T optional(Class<T> type) {
        try {
            return context.getBean(type);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
