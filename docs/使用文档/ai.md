# Summer AI（summer-ai）

> summer-ai —— 纯 JDK 实现的国内大模型对话抽象，覆盖 DeepSeek、GLM（智谱）、MiniMax、Kimi（月之暗面）等 OpenAI 兼容厂商与 OpenCode Go 订阅制聚合服务，零第三方依赖，不依赖 summer-boot。

summer-ai 提供一套 provider 无关的对话模型抽象（`ChatModel`）与链式门面（`ChatClient`），底层用 `HttpURLConnection`（阻塞式，无 selector）直连各厂商的 `OpenAI 兼容` 端点，支持同步调用与 SSE 流式调用，并解析国内思考模型的思维链（`reasoning_content`）与 token 用量。在 summer-boot 中以 `summer.ai.*` 配置自动装配，不在 classpath 时零影响。

## 快速开始

### 1. 引入依赖

`summer-boot` 以 `optional` 引入 `summer-ai`，因此需在应用中显式声明（不会传递给下游）：

```xml
<dependency>
    <groupId>cn.jiebaba.summer</groupId>
    <artifactId>summer-ai</artifactId>
    <version>3.1.0</version>
</dependency>
```

### 2. 配置厂商与密钥

在 `application.yml` 中配置：

```yaml
summer:
  ai:
    provider: deepseek              # deepseek | glm | minimax | kimi | opencode-go
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
| `summer.ai.provider` | 是 | — | 厂商：`deepseek`/`glm`/`minimax`/`kimi`/`opencode-go`，决定默认 base-url 与模型名 |
| `summer.ai.api-key` | 是 | — | 厂商 API Key，以 `Bearer` 头发送 |
| `summer.ai.model` | 否 | 厂商默认 | 模型名（如 `deepseek-chat`、`glm-5.3-flash`、`MiniMax-Text-01`、`kimi-k3`） |
| `summer.ai.base-url` | 否 | 厂商默认 | API 根地址，自动追加 `/chat/completions` |
| `summer.ai.client-name` | 否 | — | 客户端标识（`User-Agent` 头，如 `summer-ai/1.0`），OpenCode Go 等服务要求自报身份；未配置不发送 |
| `summer.ai.session-id` | 否 | — | 会话 ID（`x-opencode-session` 头，常量值）；按会话动态生成时直接构造 `OpenAiCompatibleChatModel` 传入会话 ID 供给器；未配置不发送 |
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
| `summer.ai.embedding.enabled` | 否 | `false` | 启用向量化（`EmbeddingModel`），需配合 `embedding.model` |
| `summer.ai.embedding.model` | 否 | - | 向量化模型名（如 `embedding-3`）；DeepSeek、Kimi 无 embedding 服务时指向 GLM 等 |
| `summer.ai.embedding.base-url` | 否 | 主 `base-url` | 向量化 API 根地址，自动追加 `/embeddings` |
| `summer.ai.embedding.api-key` | 否 | 主 `api-key` | 向量化 API Key，便于 chat 与 embedding 分属不同厂商 |
| `summer.ai.embedding.type` | 否 | `openai` | 向量化类型：`openai` 远程 API / `onnx` 本地推理 |
| `summer.ai.embedding.onnx.lib-path` | onnx 时必填 | - | onnxruntime 原生库路径 |
| `summer.ai.embedding.onnx.model-path` | onnx 时必填 | - | BGE-M3 ONNX 模型路径 |
| `summer.ai.embedding.onnx.tokenizer-path` | onnx 时必填 | - | tokenizer.json 路径 |
| `summer.ai.embedding.onnx.max-seq-len` | 否 | `8192` | 最大序列长度（超长截断） |
| `summer.ai.embedding.onnx.pooling` | 否 | `cls` | 池化方式：`cls` / `mean` |
| summer.ai.vectorstore.type | 否 |
one | 向量库类型：memory 内存库 / pgvector 持久化到 PostgreSQL |
| summer.ai.vectorstore.table | 否 | summer_ai_vectors | pgvector 存储表名 |
| summer.ai.vectorstore.dimensions | 否 | 空值| 向量维度，空值表示惰性探测（建议显式指定） |
| summer.ai.vectorstore.create-extension | 否 | 	rue | 是否尝试 CREATE EXTENSION vector（无权限时自动忽略） |
| summer.ai.vectorstore.create-index | 否 | 	rue | 是否创建 HNSW 余弦相似度索引 |
| `summer.ai.memory.enabled` | 否 | `false` | 启用对话记忆（`ChatMemory`） |
| `summer.ai.memory.max-messages` | 否 | `20` | 记忆窗口保留的最近消息条数 |
| `summer.ai.rag.enabled` | 否 | `false` | 启用 RAG 门面（`RagClient`），需 `vectorstore` 与 `embedding` 同时启用 |
| `summer.ai.rag.top-k` | 否 | `4` | RAG 检索返回的文档条数 |
| `summer.ai.rag.similarity-threshold` | 否 | `0.0` | RAG 相似度阈值，低于此值的结果被过滤 |
| `summer.ai.rag.instruction` | 否 | 内置默认 | RAG 注入 system 的指令文本 |
| `summer.ai.tools.enabled` | 否 | `false` | 启用工具调用循环：收集上下文中 `ToolCallback` bean，将 ChatModel 包装为 `ToolCallingChatModel`（同步与流式均自动执行工具循环） |
| `summer.ai.tools.max-iterations` | 否 | `10` | 工具调用最大轮数，超出抛 `AiException` |
| `summer.ai.logging.enabled` | 否 | `false` | 启用 AI 调用日志：将 `ChatModel` 包装为 `LoggingChatModel`，每次调用记录模型/token/耗时/成败/提问摘要 |
| `summer.ai.logging.table` | 否 | `ai_call_log` | 调用日志表名（首次写入时惰性建表，默认 `JdbcAiCallLogger` 需 `SqlExecutor`） |
| `summer.ai.models.<id>.*` | 否 | — | 命名模型实例（多模型注册表），支持项同主配置子集，见下文 |

## 多模型注册表

"不同环节用不同厂商模型"的场景（如需求分析用 DeepSeek、产品设计用 Kimi），通过命名实例配置 N 个模型并存，按 id 引用：

```yaml
summer:
  ai:
    models:
      requirement:                # 实例 id，按需命名（不支持点号）
        provider: deepseek
        api-key: sk-your-deepseek-key
        # model / base-url / timeout-seconds / temperature / max-tokens 可省略，用厂商默认
      product:
        provider: kimi
        api-key: sk-your-kimi-key
```

- 每个实例需 `provider` 与 `api-key` 齐全，否则启动快速失败并提示缺失项。
- 实例装配为 `AiModelRegistry` bean，按 id 取模型：

```java
@Autowired
private AiModelRegistry registry;

public void demo() {
    ChatModel product = registry.get("product");    // 未注册的 id 抛异常并列出可用 id
    ChatClient client = ChatClient.create(product); // 按阶段绑定不同厂商模型
}
```

- 仅配置命名实例、未配置主模型（`summer.ai.provider`）时，主 `ChatModel`/`ChatClient` 回退为注册表默认实例（字母序第一个），`getBean(ChatModel.class)` 恒可用。
- 主模型的弹性策略（重试/限流/熔断）与工具调用不作用于命名实例。

## 厂商档案

`Provider` 枚举内置默认 base-url 与默认模型名，均可被配置覆盖：

| 厂商 | 枚举 | 默认 base-url | 默认模型 |
| --- | --- | --- | --- |
| DeepSeek | `DEEPSEEK` | `https://api.deepseek.com` | `deepseek-chat` |
| 智谱 GLM | `GLM` | `https://open.bigmodel.cn/api/paas/v4` | `glm-5.3-flash` |
| MiniMax | `MINIMAX` | `https://api.minimax.chat/v1` | `MiniMax-Text-01` |
| Kimi（月之暗面） | `KIMI` | `https://api.moonshot.cn/v1` | `kimi-k3` |
| OpenCode Go（订阅制聚合） | `OPENCODE_GO` | `https://opencode.ai/zen/go/v1` | `glm-5.3-flash` |

切换新版本模型（如 `deepseek-reasoner`、`glm-5.3`、`kimi-k2.6`）时，仅需配置 `summer.ai.model`，无需改代码。`Provider.from(name)` 按名称不区分大小写解析（忽略连字符/下划线，`opencode-go`/`opencode_go`/`OPENCODE_GO` 等价），未匹配返回 `null`。

Kimi（Moonshot）注意事项：API Key 与端点区域绑定，默认指向国内端点 `api.moonshot.cn`，使用国际版 Key 时需将 `summer.ai.base-url` 覆盖为 `https://api.moonshot.ai/v1`；Kimi 不提供 embedding 端点，启用 RAG/向量库时需将 `summer.ai.embedding.base-url` 与 `summer.ai.embedding.api-key` 指向其他厂商（如 GLM）；`kimi-k3`、`glm-5.3` 等思考模型思维链计入输出 token，建议调大 `summer.ai.max-tokens` 以免回复被截断。

OpenCode Go 注意事项：订阅制聚合服务（[opencode.ai/go](https://opencode.ai/go)），`api-key` 填 OPENCODE_API_KEY；**仅 `/chat/completions` 系模型可用**（如 `glm-5.3-flash`、`kimi-k3`、`mimo-v2.6-pro`、`deepseek-v4-pro`、`longcat-2.0` 等），走 Anthropic `/messages` 协议的 MiniMax M3/M2.7、Qwen 系列与走 OpenAI `/responses` 协议的 Grok、GPT Luna、Muse Spark **不支持**，完整模型清单见[官方 Endpoints 表](https://opencode.ai/docs/go)；无 embedding 端点，启用 RAG 时需将 embedding 指向其他厂商。服务方要求客户端自报 User-Agent 并按会话携带 `x-opencode-session` 头（用于路由优化与提示缓存），可配置 `summer.ai.client-name` 与 `summer.ai.session-id` 发送（见配置项表）。

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

## 结构化输出（entity）

`entity()` / `entityResponse()` 是 `ChatClient` 的结构化输出终端方法：把模型回复直接解析为指定 Java 类型（record 或 bean），免去手工拼提示词与手写 JSON 解析。

内部流程：根据目标类型反射生成 JSON Schema，合并进 system 消息约束模型输出格式，强制 `responseFormat("json_object")`，再用 summer-boot 的 `JsonUtil` 反序列化为目标对象。

```java
// 目标结构：record 或带无参构造器的 bean 均可
record Actor(String name, int age, List<String> works) {}

Actor actor = chatClient.prompt("你是信息抽取助手")
        .user("抽取成龙的姓名、年龄与代表作")
        .entity(Actor.class);
// actor.name() -> "成龙"，actor.works() -> ["警察故事", ...]
```

需要同时拿到原始响应（token 用量、finishReason 等）时用 `entityResponse()`：

```java
StructuredResponse<Actor> sr = chatClient.prompt()
        .user("抽取刘德华的信息")
        .entityResponse(Actor.class);
Actor entity = sr.entity();               // 解析后的对象
ChatResponse response = sr.response();    // 原始响应（含 metadata().totalTokens() 等）
```

- 支持嵌套 record、`List<T>`、`Map`、数组、枚举与基本类型，字段采集规则与 `Json` 反序列化一致（record 取分量，bean 取非静态/非瞬态字段）。
- 已设置的 `ChatOptions`（model/temperature/maxTokens/tools 等）会被复用，仅强制覆盖 `responseFormat`。
- 解析时会自动剥离模型可能包裹的 Markdown 代码围栏。
- 依赖目标模型支持 `json_object` 响应格式；若厂商不支持，可改用 `call()` 自行解析 `content()`。

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

开启 JSON 模式（`responseFormat("json_object")`）让模型直接返回合法 JSON，便于结构化解析（对应 OpenAI 兼容协议的 `response_format`）。多数厂商要求提示中包含 "json" 字样，否则可能报错：

```java
ChatOptions jsonOpts = ChatOptions.builder()
        .responseFormat("json_object")   // 请求体带 response_format={"type":"json_object"}
        .build();
ChatResponse resp = chatClient.prompt()
        .system("你是 JSON 生成器，只输出 JSON 对象")
        .user("返回一个包含 ok 字段为 true 的对象")
        .options(jsonOpts)
        .call();
// resp.content() 即模型返回的 JSON 字符串
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
## 顾问链（Advisor）

顾问链是 ChatClient 的可组合拦截机制：请求按 `getOrder()` 升序依次流经各顾问，每个顾问可改写请求与响应，末端调用模型。RAG、记忆等横切能力均以顾问形式接入，可自由组合。

```
请求：调用方 -> Memory(100).beforeCall -> RAG(200).beforeCall -> ChatModel
响应：调用方 <- Memory(100).afterCall  <- RAG(200).afterCall  <- 响应
```

beforeCall 外层先执行（载入历史 -> 注入检索上下文），afterCall 内层先执行（反向）。

**核心 API**（`cn.jiebaba.summer.ai.advisor`）

- `Advisor`：顾问接口，默认实现已接入前置/后置钩子，多数顾问只需覆写 `beforeCall`/`afterCall`。
- `AdvisedRequest`：请求载体（消息 + 选项 + 跨顾问共享 context），`withMessages/withOptions` 派生新实例并共享 context。
- `AdvisedResponse`：响应载体（`ChatResponse` + context）。
- `CallAdvisorChain`/`StreamAdvisorChain`：推进下游，末端调模型。

**用法**

```java
ChatResponse resp = chatClient.prompt()
    .system("你是客服")
    .user("上次说的订单到哪了")
    .advisors(
        new MessageChatMemoryAdvisor(memory, "session-1"),
        new RetrievalAugmentationAdvisor(retriever))
    .call();
// 记忆自动载入历史，RAG 自动注入检索资料
```

顾问同样作用于 `stream()` 与 `entity()`，三者共享同一链路。

**内置顾问**

- `MessageChatMemoryAdvisor`（`ORDER=100`）：调用前载入历史、调用后回存用户与助手消息；流式在流关闭后回存完整回复。
- `RetrievalAugmentationAdvisor`（`ORDER=200`）：按最后一条 user 消息检索资料并注入 system 上下文；仍可经 `augment(Prompt)` 单独使用。

**自定义顾问**

仅覆写 `beforeCall`（请求增强）或 `afterCall`（响应处理）即可；流式需后置时覆写 `adviseStream`：

```java
public class LoggingAdvisor implements Advisor {
    @Override
    public AdvisedResponse afterCall(AdvisedResponse response) {
        System.out.println("模型回复: " + response.response().content());
        return response;
    }
}
```

- 顺序由 `getOrder()` 决定，数值越小越靠近调用方；默认 `Integer.MAX_VALUE`。
- 未配置顾问时 `call/stream/entity` 直调模型，行为与旧版一致。

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
- `SearchRequest` 支持 `topK`、`similarityThreshold`（过滤低相关结果）与 `filter`（元数据等值过滤，仅返回 metadata 含全部键值对的文档；pgvector 走 `metadata::jsonb @> ?::jsonb`，内存走谓词）。
- `SimilarityUtil.cosine(a, b)` 提供余弦相似度计算。

按元数据过滤检索（仅返回 metadata 含全部 `filter` 键值对的文档）：

```java
store.add(List.of(
        Document.builder().content("API 文档").metadata("source", "api").build(),
        Document.builder().content("内部wiki").metadata("source", "wiki").build()));
List<RetrievalResult> apiHits = store.similaritySearch(SearchRequest.builder()
        .query("文档").topK(5).filter("source", "api").build());  // 仅命中 source=api
```

生产场景可用 `JdbcVectorStore` 持久化到 PostgreSQL + pgvector：复用 summer-boot 的 `SqlExecutor` 执行 SQL（连接/事务/参数绑定/日志统一管理），向量经 `VectorTypeHandler` 与 pgvector 文本协议互转；语义与内存实现一致，重启不丢失且可跨进程共享。底层 summer-boot 的向量用法（`RowMapper`/`SqlExecutor.query` 重载/`VectorTypeHandler`）见 [orm.md](orm.md) 的「向量类型与自定义查询」一节。

```java
VectorStore store = new JdbcVectorStore(sqlExecutor, embedding,
        "summer_ai_vectors", 1024, true, true);  // 表名、维度、建扩展、建 HNSW 索引
store.add(List.of(Document.of("summer 是一个 Java 微服务框架")));
```

配置 `summer.ai.vectorstore.type=pgvector` 后由 `AiAutoConfiguration` 自动装配（`table`/`dimensions`/`create-extension`/`create-index` 可调）；`type=memory` 则用内存实现。维度建议显式指定，省略时建表前会惰性探测 `EmbeddingModel.dimensions()`（首次调用 embedding 服务）。

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
- 同步 `call` 执行完整工具循环；流式 `stream` 同样执行工具循环——逐 token 输出每轮 content、内部按 index 累积工具调用并续接下一轮，直到给出最终回复。
- 未知工具返回错误 JSON，超过最大轮数（默认 10）抛 `AiException`。

自动装配：设置 `summer.ai.tools.enabled=true` 后，`AiAutoConfiguration` 收集上下文中所有 `ToolCallback` bean（如 `@Bean ToolCallback` 或 `Tool`），自动将 `ChatModel` 包装为 `ToolCallingChatModel`；`summer.ai.tools.max-iterations` 可调最大轮数。无需手动 `new ToolCallingChatModel(...)`，注入 `ChatClient`/`ChatModel` 即可直接使用工具循环。

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

### AI 调用日志 / 观测性

`LoggingChatModel` 在底层模型之上叠加调用日志：每次 `call`/`stream` 完成后采集模型名、token 用量、耗时、成败与提问摘要，交由 `AiCallLogger` 落库或输出。装饰器位于最内层（紧贴 `OpenAiCompatibleChatModel`），因此重试与工具循环的每一轮真实 LLM 调用都会被记录，token 成本统计最精确。

```java
AiCallLogger logger = new JdbcAiCallLogger(sqlExecutor);   // 写入 ai_call_log 表
ChatModel model = new LoggingChatModel(chatModel, logger);
```

- 配置 `summer.ai.logging.enabled=true` 后，`AiAutoConfiguration` 自动将 `ChatModel` 包装为 `LoggingChatModel`；`summer.ai.logging.table` 可自定义表名（默认 `ai_call_log`）。
- 默认 `JdbcAiCallLogger` 复用 summer-boot 的 `SqlExecutor`，首次写入时惰性建表（`CREATE TABLE IF NOT EXISTS`）；也可注册自定义 `AiCallLogger` Bean 接管（如写日志框架或外部观测系统）。
- 日志写入异常被吞掉且仅首次输出到标准错误，不影响主调用链；流式调用经 `peek` 捕获末帧 usage、`onClose` 落库（调用方应及时关闭返回的 `Stream`）。

`ai_call_log` 表结构：

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGSERIAL` | 主键 |
| `create_time` | `TIMESTAMP` | 默认当前时间 |
| `model` | `TEXT` | 模型名（无元数据时为 NULL） |
| `prompt_tokens` | `INT` | 输入 token |
| `completion_tokens` | `INT` | 输出 token |
| `total_tokens` | `INT` | 合计 token |
| `latency_millis` | `BIGINT` | 耗时毫秒 |
| `success` | `BOOLEAN` | 是否成功 |
| `error_message` | `TEXT` | 失败时的错误信息 |
| `query_summary` | `TEXT` | 首条 user 消息摘要（超 200 字截断） |

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
| `toolCalls` | 工具调用列表（`ToolCall` 含 `id`/`name`/`arguments`/`index`）；模型请求调用工具时返回，流式时按 `index` 累积增量片段 |
| `metadata` | 用量元数据（同步响应含 usage；流式时 stream_options.include_usage 末帧亦含） |

`ChatResponseMetadata` 记录 token 用量：

| 字段 | 说明 |
| --- | --- |
| `model` | 实际响应的模型名 |
| `promptTokens` | 输入 token 数 |
| `completionTokens` | 输出 token 数 |
| `totalTokens` | 总 token 数 |
| `promptCacheHitTokens` | 缓存命中 token 数（DeepSeek 等厂商提供，未提供时为 `null`） |

## 自动配置原理

`AiAutoConfiguration`（`@Configuration`，位于 summer-boot）按 `summer.ai.*` 装配以下 Bean：

- `AiProperties` -- 从环境配置解析厂商档案与参数；
- `AiModelRegistry` -- 按 `summer.ai.models.<id>.*` 为每个命名实例创建独立 `ChatModel`（见"多模型注册表"），主模型未配置时主 `ChatModel` 回退为注册表默认实例；
- `ChatModel` -- `OpenAiCompatibleChatModel` 实例，未正确配置 provider 且无命名实例时快速失败；启用 `summer.ai.logging.enabled=true` 时最内层叠加 `LoggingChatModel` 记录调用日志，配置重试/限流/熔断参数时按需叠加 `ResilientChatModel`，启用 `summer.ai.tools.enabled=true` 且存在 `ToolCallback` bean 时再叠加 `ToolCallingChatModel` 自动执行工具循环（同步与流式均支持）；
- `ChatClient` -- 以 `ChatModel` 创建的门面单例；
- `EmbeddingModel` -- 按 `summer.ai.embedding.type` 装配：`openai`（默认）用 `OpenAiCompatibleEmbeddingModel` 走远程 API，`onnx` 用 `OnnxEmbeddingModel` 本地 ONNX 推理（int64 输入、FP16 输出、CLS/mean 池化、L2 归一化）；
- `VectorStore` / `ChatMemory` / `RagClient` -- 以 `@Lazy` 按需装配，仅启用对应配置并注入时才初始化；未启用而强行注入时抛出明确异常，不影响未使用这些能力的应用启动。

summer-boot 以 `optional` 引入 summer-ai，采用仿 `@ConditionalOnClass` 的存在性探测：`SummerApplication` 启动时探测 `cn.jiebaba.summer.ai.chat.ChatModel` 是否在 classpath，在才注册 `AiAutoConfiguration`，不在则该类永不被加载（不会 `NoClassDefFoundError`）。因此不引入 summer-ai 依赖的应用零影响。

## 独立使用（不依赖 summer-boot）

summer-ai 依赖 summer-boot（用其 `JsonUtil` 等 core 能力），模块内自行构造 `OpenAiCompatibleChatModel` 亦可脱离容器使用：

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

## 本地 ONNX Embedding（BGE-M3）

除远程 API 向量化（`embedding.type=openai`）外，summer-ai 支持本地 ONNX 推理向量化（`embedding.type=onnx`）：经 `OnnxEmbeddingModel` 加载 BGE-M3 等 XLM-RoBERTa 系 ONNX 模型，分词后以 int64 token ids 输入，读取 FP16 输出做 CLS/mean 池化与 L2 归一化。纯 JDK + FFM 调用 onnxruntime，不联网、无 API 成本、低延迟，适合离线与高隐私场景。

> `OnnxEmbeddingModel` 复用 summer-boot 的共享 `OnnxEngine`（FFM 直连 onnxruntime），与 OCR 同一套原生库与加载机制。资产下载见 [模型与原生库下载指南](model-assets.md)。
>
> **onnxruntime 版本**：`OnnxEngine` 请求 `ORT_API_VERSION=20`（1.16 引入），onnxruntime C API 仅追加不重排、`GetApi` 向后兼容，因此 1.16 至最新 1.27.x 均可加载，推荐最新稳定版 1.27.1（CPU 版即可）。

### 配置

```yaml
summer:
  ai:
    embedding:
      enabled: true
      type: onnx                      # openai 远程 | onnx 本地
      onnx:
        lib-path: D:/ai/onnxruntime.dll
        model-path: D:/ai/bge-m3/model.onnx
        tokenizer-path: D:/ai/bge-m3/tokenizer.json
        max-seq-len: 8192             # BGE-M3 最大序列长度
        pooling: cls                  # cls（默认）| mean
```

> 运行需 `--enable-native-access=ALL-UNNAMED`（FFM 调用原生库所需，summer-boot 启动脚本已内置，原因详见 [模型下载指南](model-assets.md)）。

### 注入使用

`embedding.type=onnx` 时 `EmbeddingModel` 自动装配为 `OnnxEmbeddingModel`，与远程版用法一致，可直接配合 `VectorStore`/`RagClient`：

```java
@Autowired
private EmbeddingModel embeddingModel;

float[] vec = embeddingModel.embed("summer 框架").embeddings().get(0).vector();
```

### 独立使用

```java
EmbeddingModel model = new OnnxEmbeddingModel(
        "D:/ai/onnxruntime.dll",            // onnxruntime 原生库
        "D:/ai/bge-m3/model.onnx",          // BGE-M3 FP16 ONNX
        "D:/ai/bge-m3/tokenizer.json",      // XLM-RoBERTa 分词器
        8192,                                // 最大序列长度
        "cls");                              // 池化方式
float[] vec = model.embed("你好").embeddings().get(0).vector();
model.close();
```

## 核心架构

```
调用方 ──> ChatClient（fluent 门面）
              └──> ChatModel（provider 无关接口，按需叠加装饰器）
                       ├── [可选] ToolCallingChatModel（summer.ai.tools.enabled=true，收集 ToolCallback bean 自动执行工具循环）
                       ├── [可选] ResilientChatModel（配置重试/限流/熔断时叠加）
                       ├── [可选] LoggingChatModel（summer.ai.logging.enabled=true，记录每次调用的模型/token/耗时/成败）
                       └──> OpenAiCompatibleChatModel（OpenAI 兼容实现）
                                ├── 同步：HttpURLConnection POST /chat/completions -> 解析 JSON
                                └── 流式：stream=true -> 逐行解析 SSE data 帧 -> Stream<ChatResponse>
```

- **纯 JDK**：用 `HttpURLConnection`（阻塞式）而非 selector，与 summer-boot 同样规避受限沙箱的 loopback 管道问题；JSON 序列化/解析复用 summer-boot 的 `JsonUtil`，零第三方依赖。
- **OpenAI 兼容**：DeepSeek、GLM（智谱 OpenAI 兼容端点）、MiniMax、OpenCode Go 仅 base-url 与模型名不同，请求、响应与 SSE 流式协议一致。
- **思维链**：解析 `message`/`delta` 中的 `reasoning_content`，兼容 DeepSeek-R1 等思考模型的思维链输出。
- **token 用量**：同步响应解析 `usage`（含 `prompt_cache_hit_tokens`），便于成本监控与缓存命中率统计。

## 进程内多 agent 协作

summer-ai 的 `cn.jiebaba.summer.ai.agent` 包提供单 JVM 进程内的多 agent 协作能力：
把一个任务（`AgentTask`）按技能名路由给本地注册的 `AgentExecutor`，由
`AgentOrchestrator` 统一编排（同步/异步/并行、超时兜底、协作链防环）。
纯内存编排：无端口、无文件、无外部依赖，跨进程协作语义留给应用侧自行实现。

### 核心 SPI 与配置

- `AgentExecutor`：执行器 SPI（`name()` + `run(AgentTask)`），实现可组合 ChatClient/工具循环/RAG，也可为纯逻辑；
- `AgentTask`：协作任务信封（taskId/conversationId/skill/input/feedback/context），
  `begin(skill, input)` 新建，`withFeedback`/`withContext`/`inConversation` 派生新任务；
- `AgentResult`：执行结果（success/output/evidence/error），`ok/fail` 静态工厂；
- `AgentPolicy`：协作策略（超时秒数 + 协作链最大深度），`ofDefaults()` = (300, 8)；
- `AgentOrchestrator`：按技能名路由的进程内编排器：
  - `dispatch(task)` / `dispatch(skill, input)`：同步路由并等待结果（超时取消执行并判失败）；
  - `submit(task)`：异步派发，返回 `CompletableFuture<AgentResult>`；
  - `dispatchAll(tasks)`：并行扇出，结果按入参顺序返回；
  - `skills()`：已注册技能名录（诊断用）；
  - 防环：以 JDK 25 正式版 `ScopedValue` 携带协作链（已访问技能序列），执行前查重判环、
    查深度上限——嵌套 dispatch 跨虚拟线程也能感知完整链路；未知技能在派发线程上快速失败
    （不进线程池）；执行器表惰性解析一次，同名重复注册记 WARNING 并保留先注册者。

自动装配（随 summer-ai 模块发布，默认开启；`summer.ai.enabled=false` 整体关闭）：

| 配置 | 说明 | 默认 |
|---|---|---|
| `summer.ai.enabled` | AI 自动配置总开关 | true（false 整体关闭） |
| `summer.ai.agent.timeout-seconds` | 单次任务同步等待上限 | 300 |
| `summer.ai.agent.max-depth` | 协作链最大深度（防嵌套成环） | 8 |

`AgentPolicy` 与 `AgentOrchestrator` 均带 `@ConditionalOnMissingBean` 退避：应用自定义同名
bean 即接管装配。

> **凭证纪律**：`AgentResult.success=true` 只证明执行结束，不证明任务达成——完成判定应以
> 下游可核验的 `evidence`（产物哈希/退出码/校验摘要等）为准，执行器实现有义务产出证据，
> 编排调用方核证后再交接，不采信执行器自述成功。

### 最小用法示例

两个执行器（skill = "writer" / "reviewer"）经 `@Component` 注册进上下文后：

```java
// 产出初稿
AgentResult draft = orchestrator.dispatch("writer", "summer 框架介绍");
// 审查（与初稿同会话，可透传上下文）
AgentResult review = orchestrator.dispatch(AgentTask.begin("reviewer", draft.output())
        .inConversation("conv-1")
        .withContext(Map.of("scene", "官网文案")));
// 按审查反馈修订
AgentResult revised = orchestrator.dispatch(AgentTask.begin("writer", "summer 框架介绍")
        .inConversation("conv-1")
        .withFeedback(review.output()));
```

summer-sample 提供离线可跑的最小示例：GET `/agent/demo?topic=...`（桩执行器纯字符串处理，
演示「产出 → 审查 → 修订」完整协作链）。

## 装配解耦与动态模型注册（4.0 增强）

- **缺配置不再拒启**：`ChatModel`/`ChatClient` 懒装配（注入时才实例化）。模型完全由应用运行期
  动态提供时，无需任何 `summer.ai.*` 配置；显式 `summer.ai.enabled=false` 整体关闭自动装配。
- **`AiModelRegistry` 运行期注册/注销 + 多级回落**（服务"按数字员工实例绑定模型、未绑定回落
  角色默认"类场景）：

```java
registry.register("developer-lin", model);      // 运行期热绑定
registry.unregister("developer-lin");
registry.resolve("developer-lin", "developer"); // 回落链：实例 → 角色默认
registry.getOptional(id);                       // 自行决定回落策略
```

- **维度化用量计量 `UsageMeter`**：按 tags（run/stage/role/instance）记录/聚合 token 消耗，
  自动装配 `JdbcUsageMeter`（表 `ai_usage`，`summer.ai.usage.table` 可改）或 `InMemoryUsageMeter`；
  `sumGroupedBy` 分组合计 === `sum` 总计（可加性恒等式）。

详见 [4.0 增强速览](whats-new-4.0.md) §5。

## 后续规划

summer-ai 核心能力（对话/向量化/向量库/工具调用/RAG/记忆/重试/调用日志/多模态）已就绪，以下为待开发项（与 `roadmap.md` 第八阶段「待开发」同步）：

- **DB 驱动厂商凭据（生产级）**：`AiAutoConfiguration` 支持从 `ai_provider_info` 表实时读取 provider/base-url/api-key（当前仅冒烟测试实现，未接入自动配置）。
- **示例 AI 端点**：summer-sample 增加 `/ai/**` 控制器（对话/流式/RAG/工具调用示例），目前 `summer.ai.*` 段为注释状态。
