# Summer AI（summer-ai）

> summer-ai —— 纯 JDK 实现的国内大模型对话抽象，覆盖 DeepSeek、GLM（智谱）、MiniMax 等 OpenAI 兼容厂商，零第三方依赖，不依赖 summer-boot。

summer-ai 提供一套 provider 无关的对话模型抽象（`ChatModel`）与链式门面（`ChatClient`），底层用 `HttpURLConnection`（阻塞式，无 selector）直连各厂商的 `OpenAI 兼容` 端点，支持同步调用与 SSE 流式调用，并解析国内思考模型的思维链（`reasoning_content`）与 token 用量。在 summer-boot 中以 `summer.ai.*` 配置自动装配，不在 classpath 时零影响。

## 快速开始

### 1. 引入依赖

`summer-boot` 以 `optional` 引入 `summer-ai`，因此需在应用中显式声明（不会传递给下游）：

```xml
<dependency>
    <groupId>cn.jiebaba.summer</groupId>
    <artifactId>summer-ai</artifactId>
    <version>3.0.0</version>
</dependency>
```

### 2. 配置厂商与密钥

在 `application.yml` 中配置：

```yaml
summer:
  ai:
    provider: deepseek              # deepseek | glm | minimax
    api-key: sk-your-api-key-here
    # 以下均可省略，使用厂商默认值
    # model: deepseek-chat
    # base-url: https://api.deepseek.com
    # timeout-seconds: 60
    # temperature: 0.7
    # max-tokens: 2048
```

未配置 `summer.ai.provider` 与 `summer.ai.api-key` 时，启动会快速失败并提示缺少配置。

### 3. 注入并调用

```java
@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private ChatClient chatClient;

    @GetMapping("/chat")
    public String chat(@RequestParam String q) {
        return chatClient.prompt("你是一名简洁的助手")
                .user(q)
                .call()
                .content();
    }
}
```

## 配置项

`summer.ai.*` 由 `AiProperties` 绑定（`summer-boot` 的 `AiAutoConfiguration` 装配）：

| 配置项 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `summer.ai.provider` | 是 | — | 厂商：`deepseek`/`glm`/`minimax`，决定默认 base-url 与模型名 |
| `summer.ai.api-key` | 是 | — | 厂商 API Key，以 `Bearer` 头发送 |
| `summer.ai.model` | 否 | 厂商默认 | 模型名（如 `deepseek-chat`、`glm-4`、`MiniMax-Text-01`） |
| `summer.ai.base-url` | 否 | 厂商默认 | API 根地址，自动追加 `/chat/completions` |
| `summer.ai.timeout-seconds` | 否 | `60` | 连接与读取超时（秒） |
| `summer.ai.temperature` | 否 | `0.7` | 采样温度，全局默认，可被单次 `ChatOptions` 覆盖 |
| `summer.ai.max-tokens` | 否 | `2048` | 最大生成 token 数，可被单次 `ChatOptions` 覆盖 |
| `summer.ai.retry.max-attempts` | 否 | `1` | 最大重试次数（>1 启用重试，包装为 `ResilientChatModel`） |
| `summer.ai.retry.initial-backoff-millis` | 否 | `500` | 首次退避毫秒 |
| `summer.ai.retry.multiplier` | 否 | `2.0` | 退避倍率（指数退避） |
| `summer.ai.retry.max-backoff-millis` | 否 | `20000` | 退避上限毫秒 |
| `summer.ai.rate-limit.permits-per-second` | 否 | `0` | 令牌桶限流速率（>0 启用，0 禁用） |
| `summer.ai.circuit-breaker.failure-threshold` | 否 | `0` | 熔断失败阈值（>0 启用熔断） |
| `summer.ai.circuit-breaker.wait-millis` | 否 | `30000` | 熔断开启后冷却毫秒 |

## 厂商档案

`Provider` 枚举内置默认 base-url 与默认模型名，均可被配置覆盖：

| 厂商 | 枚举 | 默认 base-url | 默认模型 |
| --- | --- | --- | --- |
| DeepSeek | `DEEPSEEK` | `https://api.deepseek.com` | `deepseek-chat` |
| 智谱 GLM | `GLM` | `https://open.bigmodel.cn/api/paas/v4` | `glm-4` |
| MiniMax | `MINIMAX` | `https://api.minimax.chat/v1` | `MiniMax-Text-01` |

切换新版本模型（如 `deepseek-reasoner`、`glm-4-plus`）时，仅需配置 `summer.ai.model`，无需改代码。`Provider.from(name)` 按名称不区分大小写解析，未匹配返回 `null`。

## ChatClient 门面

`ChatClient` 是 `ChatModel` 的 fluent 门面，链式拼装消息与选项后发起调用：

```java
ChatClient client = ChatClient.create(chatModel);

// 同步调用
ChatResponse resp = client.prompt("你是翻译助手")
        .user("把 hello 翻译成中文")
        .call();

String answer = resp.content();           // 模型回复
String thinking = resp.reasoningContent(); // 思维链（思考模型才有）

// 流式调用（逐 token）
try (Stream<ChatResponse> stream = client.prompt()
        .system("你是诗人")
        .user("写一首关于夏天的五言绝句")
        .stream()) {
    stream.forEach(chunk -> System.out.print(chunk.content()));
}
```

- `prompt()` 创建空请求；`prompt(String systemText)` 同时设定 system 消息。
- `system()/user()/assistant()` 累积多轮消息。
- `options(ChatOptions)` 覆盖本次调用的模型/温度/最大 token。
- 流式返回的 `Stream<ChatResponse>` 应在 try-with-resources 中使用，关闭时自动释放连接。

## ChatOptions

单次调用可选项，覆盖全局默认：

```java
ChatOptions options = ChatOptions.builder()
        .model("deepseek-reasoner")   // 切换为思考模型
        .temperature(0.3)
        .maxTokens(4096)
        .build();

ChatResponse resp = chatClient.prompt()
        .system("你是代码评审员")
        .user(code)
        .options(options)
        .call();
```

## 多轮对话

`ChatClient` 的 builder 可累积历史消息，实现多轮上下文：

```java
public class ChatSession {
    private final ChatClient client;
    private final List<Message> history = new ArrayList<>();

    public ChatSession(ChatClient client, String systemPrompt) {
        this.client = client;
        this.history.add(Message.system(systemPrompt));
    }

    public String ask(String userInput) {
        history.add(Message.user(userInput));
        var builder = client.prompt();
        history.forEach(m -> {
            switch (m.role()) {
                case "system" -> builder.system(m.content());
                case "user" -> builder.user(m.content());
                case "assistant" -> builder.assistant(m.content());
            }
        });
        String answer = builder.call().content();
        history.add(Message.assistant(answer));
        return answer;
    }
}
```

> 注意：`PromptBuilder` 每次调用重置，本身不持有跨调用状态，多轮上下文需由调用方维护消息列表。
## 扩展能力

summer-ai 在对话核心之外提供向量库、工具调用、RAG、多模态、记忆与弹性六大扩展能力，均为纯 JDK 实现，零第三方依赖，可按需组合：

```
ResilientChatModel ──> ToolCallingChatModel ──> ChatClient ──> MemoryChatClient / RagClient
        (弹性)              (工具循环)          (fluent 门面)        (记忆 / RAG)
```

### Embedding 与向量库

`EmbeddingModel` 抽象向量化，`OpenAiCompatibleEmbeddingModel` 直连各厂商 `/embeddings` 端点；`VectorStore` 抽象存储与检索，`InMemoryVectorStore` 提供余弦相似度内存实现：

```java
EmbeddingModel embedding = new OpenAiCompatibleEmbeddingModel(
        "https://api.deepseek.com", "sk-xxx", "text-embedding", Duration.ofSeconds(60));

VectorStore store = new InMemoryVectorStore(embedding);
store.add(List.of(Document.of("summer 是一个 Java 微服务框架")));
List<RetrievalResult> hits = store.similaritySearch(
        SearchRequest.builder().query("Java 框架").topK(3).similarityThreshold(0.3).build());
```

- `Document` 含 `id`/`content`/`metadata`，`id` 缺省时自动生成 UUID。
- `SearchRequest` 支持 `topK` 与 `similarityThreshold`（过滤低相关结果）。
- `SimilarityUtil.cosine(a, b)` 提供余弦相似度计算。

### Function Calling / 工具调用

`ToolCallingChatModel` 在任意 `ChatModel` 之上叠加工具循环：注入工具定义 -> 模型返回 `tool_calls` -> 执行工具 -> 回填结果 -> 继续，直到给出最终回复或达到最大轮数：

```java
Tool weather = new Tool("getWeather", "查询城市天气",
        List.of(ToolParameter.string("city", "城市名称")),
        args -> Map.of("city", args.get("city"), "weather", "晴"));

ChatModel model = new ToolCallingChatModel(chatModel, List.of(weather));
String answer = ChatClient.create(model).prompt().user("北京天气").call().content();
```

- `Tool` 由 `ToolParameter` 自动生成 JSON Schema，执行体接收参数 `Map`、返回任意对象（自动序列化为 JSON）。
- 同步 `call` 执行完整工具循环；流式 `stream` 直接透传底层模型。
- 未知工具返回错误 JSON，超过最大轮数（默认 10）抛 `AiException`。

### RAG 检索增强与文档分块

`Document`/`DocumentReader`/`TextSplitter`/`TokenTextSplitter` 负责加载与分块；`Retriever`/`VectorStoreRetriever` 负责检索；`RetrievalAugmentationAdvisor` 将检索到的资料注入提问；`RagClient` 一步完成「检索 + 增强 + 调用」：

```java
VectorStore store = new InMemoryVectorStore(embedding);
List<Document> docs = new TokenTextSplitter(300, 30).split(longDocument);
store.add(docs);

RetrievalAugmentationAdvisor advisor = new RetrievalAugmentationAdvisor(new VectorStoreRetriever(store, 3));
RagClient rag = new RagClient(ChatClient.create(chatModel), advisor);
String answer = rag.ask("你是助手", "summer 如何使用 AOP").content();
```

- `TokenTextSplitter` 兼顾中英文近似 token 切分并带 overlap，切块取原始子串、保留空白与标点。
- `RetrievalAugmentationAdvisor.augment(Prompt)` 在用户提问前插入「参考资料」system 消息；无结果时不改写。

### 多模态消息

`UserMessage` 支持 `ContentPart` 内容片段：`TextPart`（文本）、`ImageUrlPart`（图片，支持 http 链接或 base64 数据 URI）、`InputAudioPart`（语音，base64 + wav/mp3）：

```java
UserMessage msg = UserMessage.of(
        new TextPart("这张图里是什么？"),
        new ImageUrlPart("https://example.com/cat.png", "high"));

ChatResponse resp = chatClient.prompt().messages(List.of(msg)).call();
```

- 纯文本用户消息仍以字符串 `content` 发送，保持原有请求格式不变。
- 多片段时序列化为 OpenAI `content` 数组；assistant 消息可携带 `tool_calls`，`ToolMessage` 承载工具结果。

### 对话记忆与会话管理

`ChatMemory` 抽象按会话 id 维护历史，`MessageWindowChatMemory` 保留最近 N 条且始终保留首条 system 消息；`MemoryChatClient` 自动载入/回存历史：

```java
ChatMemory memory = new MessageWindowChatMemory(20);
MemoryChatClient client = new MemoryChatClient(ChatClient.create(chatModel), memory, "user-1");
String r1 = client.call("我叫小明").content();
String r2 = client.call("我叫什么").content();  // 可记忆上一轮
client.clear();  // 清空会话
```

### 重试、限流与超时熔断

`ResilientChatModel` 在底层模型之上叠加 `RetryPolicy`（指数退避重试）、`RateLimiter`（令牌桶限流）、`CircuitBreaker`（CLOSED/OPEN/HALF_OPEN 熔断），三者均可选：

```java
ChatModel resilient = new ResilientChatModel(
        chatModel,
        new RateLimiter(5),                                   // 5 次/秒
        RetryPolicy.builder().maxAttempts(3).build(),        // 最多 3 次
        new CircuitBreaker(5, Duration.ofSeconds(30)));      // 5 次失败熔断 30s
```

- 配置 `summer.ai.retry.*`/`summer.ai.rate-limit.*`/`summer.ai.circuit-breaker.*` 后，`AiAutoConfiguration` 自动将 `ChatModel` 包装为 `ResilientChatModel`；不配置则行为与原来完全一致。
- 熔断开启时抛 `CircuitBreakerOpenException` 快速失败，避免持续冲击下游；流式调用仅做限流与熔断许可、不重试。

## 底层 ChatModel 接口

不使用 `ChatClient` 时，可直接操作 `ChatModel` 与 `Prompt`：

```java
ChatModel model = new OpenAiCompatibleChatModel(
        "https://api.deepseek.com", "sk-xxx", "deepseek-chat",
        Duration.ofSeconds(60), 0.7, 2048);

List<Message> messages = List.of(
        Message.system("你是助手"),
        Message.user("你好"));
ChatResponse resp = model.call(new Prompt(messages));
```

- `Message.system(content)` / `Message.user(content)` / `Message.assistant(content)` 为快捷工厂方法。
- `Prompt` 持有不可变消息列表与可选 `ChatOptions`。

## 响应结构

`ChatResponse` 为 record，同步调用返回完整响应，流式调用返回增量片段：

| 字段 | 说明 |
| --- | --- |
| `content` | 模型回复文本；流式时为增量片段 |
| `reasoningContent` | 思维链（`reasoning_content`），DeepSeek 等思考模型特有字段，普通模型为 `null` |
| `finishReason` | 结束原因（如 `stop`、`length`） |
| `metadata` | 用量元数据（同步响应才有） |

`ChatResponseMetadata` 记录 token 用量：

| 字段 | 说明 |
| --- | --- |
| `model` | 实际响应的模型名 |
| `promptTokens` | 输入 token 数 |
| `completionTokens` | 输出 token 数 |
| `totalTokens` | 总 token 数 |
| `promptCacheHitTokens` | 缓存命中 token 数（DeepSeek 等厂商提供，未提供时为 `null`） |

## 自动配置原理

`AiAutoConfiguration`（`@Configuration`，位于 summer-boot）按 `summer.ai.*` 装配三个 Bean：

- `AiProperties` —— 从环境配置解析厂商档案与参数；
- `ChatModel` —— `OpenAiCompatibleChatModel` 实例，未正确配置 provider 时快速失败；
- `ChatClient` —— 以 `ChatModel` 创建的门面单例。

summer-boot 以 `optional` 引入 summer-ai，采用仿 `@ConditionalOnClass` 的存在性探测：`SummerApplication` 启动时探测 `cn.jiebaba.summer.ai.chat.ChatModel` 是否在 classpath，在才注册 `AiAutoConfiguration`，不在则该类永不被加载（不会 `NoClassDefFoundError`）。因此不引入 summer-ai 依赖的应用零影响。

## 独立使用（不依赖 summer-boot）

summer-ai 仅依赖 summer-core（用其 `JsonUtil`），可脱离 summer-boot 单独使用，自行构造 `OpenAiCompatibleChatModel`：

```java
ChatModel model = new OpenAiCompatibleChatModel(
        "https://api.deepseek.com", "sk-xxx", "deepseek-chat",
        Duration.ofSeconds(60), 0.7, 2048);

ChatResponse resp = ChatClient.create(model)
        .prompt("你是助手")
        .user("用一句话介绍虚拟线程")
        .call();
System.out.println(resp.content());
```

## 核心架构

```
调用方 ──> ChatClient（fluent 门面）
              └──> ChatModel（provider 无关接口）
                       └──> OpenAiCompatibleChatModel（OpenAI 兼容实现）
                                ├── 同步：HttpURLConnection POST /chat/completions -> 解析 JSON
                                └── 流式：stream=true -> 逐行解析 SSE data 帧 -> Stream<ChatResponse>
```

- **纯 JDK**：用 `HttpURLConnection`（阻塞式）而非 selector，与 summer-web 同样规避受限沙箱的 loopback 管道问题；JSON 序列化/解析复用 summer-core 的 `JsonUtil`，零第三方依赖。
- **OpenAI 兼容**：DeepSeek、GLM（智谱 OpenAI 兼容端点）、MiniMax 仅 base-url 与模型名不同，请求、响应与 SSE 流式协议一致。
- **思维链**：解析 `message`/`delta` 中的 `reasoning_content`，兼容 DeepSeek-R1 等思考模型的思维链输出。
- **token 用量**：同步响应解析 `usage`（含 `prompt_cache_hit_tokens`），便于成本监控与缓存命中率统计。
