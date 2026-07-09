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
