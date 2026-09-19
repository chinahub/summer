# SSE（服务端事件推送）

> summer-boot 内置 Server-Sent Events 支持：handler 返回 `SseEmitter` 即可向浏览器（`EventSource`）
> 或任意 HTTP 客户端逐事件推送数据；响应采用 `Transfer-Encoding: chunked` 分块编码并保持
> keep-alive 连接复用。典型场景：LLM 流式输出转发、实时行情、任务进度推送。

## 快速开始

```java
@RestController
public class SseController {

    /** 返回 SseEmitter 后请求线程由框架接管，任意线程可调用 send 推送事件。 */
    @GetMapping("/sse/time")
    public SseEmitter time() {
        SseEmitter emitter = new SseEmitter(0);   // 0 = 无空闲超时
        Thread.startVirtualThread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    emitter.send(SseEvent.of("tick-" + i, "tick")); // event: tick / data: tick-i
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ignored) {
                emitter.completeWithError(new IllegalStateException("interrupted"));
                return;
            }
            emitter.complete();
        });
        return emitter;
    }
}
```

客户端接收：

```js
const es = new EventSource("/sse/time");
es.addEventListener("tick", e => console.log(e.data));   // tick-0 ...
```

```bash
curl -N http://localhost:8080/sse/time
```

## 响应协议

- 响应头：`Content-Type: text/event-stream`、`Cache-Control: no-cache`、
  `X-Accel-Buffering: no`（禁用 nginx 代理缓冲）、`Transfer-Encoding: chunked`
- 帧：`data:`/`event:`/`id:`/`retry:`（见 SseEvent），data 多行自动拆分为多个 `data:` 行，帧尾空行
- 连接：chunked + keep-alive，流结束（终结块）后连接可继续服务下一请求；
  客户端断开（写失败）时连接关闭

## SseEmitter API

| 方法 | 说明 |
| --- | --- |
| `send(Object data)` | 推送一条事件（混合编码，见下）；完成后调用抛 `IllegalStateException` |
| `complete()` | 正常结束事件流 |
| `completeWithError(Throwable)` | 异常结束流，触发 `onError` 回调 |
| `onCompletion(Runnable)` / `onTimeout(Runnable)` / `onError(Consumer<Throwable>)` | 生命周期回调（由框架在请求线程触发） |
| `SseEmitter(long timeoutMillis)` | 队列空闲超时（毫秒），超时触发 `onTimeout` 并结束流；默认 0 = 无限等待 |

## 事件编码（混合判定）

`send` 的对象按类型编码：

| 元素类型 | 编码 |
| --- | --- |
| `SseEvent` | 完整帧（`event:`/`id:`/`retry:`/`data:`） |
| `String` | 原样作为 `data:`（多行拆分为多个 data 行） |
| 其他对象 | JSON 序列化后作为 `data:`（MessageConverter，默认 `JsonMessageConverter`） |

LLM 场景的 `data: [DONE]` 终结帧由业务流自行发送（可用 `SseEvent.of("[DONE]", "done")` 带事件名），
框架不隐式写入。

## 与 summer-ai 流式模型结合

`ChatModel.stream()` 返回 `Stream<ChatResponse>`，在后台虚拟线程中消费并经 emitter 转发即可实现
LLM 输出的浏览器流式呈现（完整示例见 summer-sample 的 `/ai/stream` 端点）：

```java
@GetMapping("/ai/stream")
public SseEmitter stream(@RequestParam("q") String question) {
    SseEmitter emitter = new SseEmitter(0);
    Thread.startVirtualThread(() -> {
        try (Stream<ChatResponse> s = chatClient.prompt("你是一名助手").user(question).stream()) {
            s.forEach(chunk -> {
                if (chunk.content() != null && !chunk.content().isEmpty()) emitter.send(chunk.content());
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
```

## 注意事项

- `send` 应在 handler 返回之前或之后任意线程调用；完成后（complete/completeWithError）再调用会抛异常
- 长时间无事件的连接建议设置 `SseEmitter(timeoutMillis)` 空闲超时，避免连接滞留（客户端代理也可能有各自的空闲断开策略）
- 超过 `server.max-requests-per-connection` 不影响单次 SSE 响应（计数按请求，SSE 也是一次请求）
- gzip 压缩不会作用于已提交的 SSE 流（流式响应在压缩判定前已提交）
