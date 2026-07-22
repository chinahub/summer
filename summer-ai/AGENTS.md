# summer-ai 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-ai/` 目录下的文件时才会加载本文件。

## 模块职责

大模型对话抽象 + RAG + 工具调用 + 向量存储 + 重试/熔断。依赖 summer-core。纯 JDK HTTP 客户端实现。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summe.ai.chat` | 对话核心：ChatModel/ChatClient/Message/ChatOptions/ChatResponse/Prompt/ToolDefinition |
| `cn.jiebaba.summe.ai.chat.content` | 多模态内容：TextPart/ImageUrlPart/InputAudioPart |
| `cn.jiebaba.summe.ai.model.openai` | OpenAI 兼容模型：OpenAiCompatibleChatModel（DeepSeek/GLM/MiniMax 等） |
| `cn.jiebaba.summe.ai.rag` | RAG：RagClient/RetrievalAugmentationAdvisor/VectorStoreRetriever |
| `cn.jiebaba.summe.ai.vectorstore` | 向量存储：VectorStore/InMemoryVectorStore/SearchRequest/SimilarityUtil |
| `cn.jiebaba.summe.ai.embedding` | 嵌入模型：EmbeddingModel + OpenAI 兼容实现 |
| `cn.jiebaba.summe.ai.tools` | 工具调用：ToolCallingChatModel/Tool/ToolCallback |
| `cn.jiebaba.summe.ai.memory` | 对话记忆：ChatMemory/MessageWindowChatMemory/MemoryChatClient |
| `cn.jiebaba.summe.ai.retry` | 重试与熔断：ResilientChatModel/RetryPolicy/RateLimiter/CircuitBreaker |
| `cn.jiebaba.summe.ai.document` | 文档处理：DocumentReader/TextSplitter/TokenTextSplitter/Document |
| `cn.jiebaba.summe.ai.logging` | AI 调用日志：LoggingChatModel/AiCallLogger/AiCallLog |

## 模块约定

- **纯 JDK HTTP**：使用 `java.net.http.HttpClient` 调用 API，不依赖 OkHttp 或 Apache HttpClient
- **OpenAI 兼容**：`OpenAiCompatibleChatModel` 支持所有 OpenAI API 兼容的服务（DeepSeek/GLM/MiniMax 等）
- **流式**：支持 SSE 流式响应（`Server-Sent Events`）
- **工具调用**：`ToolCallingChatModel` 包装 ChatModel，自动处理 function calling 循环
- **重试**：`ResilientChatModel` 提供指数退避重试 + 限流 + 熔断
