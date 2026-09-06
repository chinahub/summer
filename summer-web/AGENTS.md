# summer-web 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-web/` 目录下的文件时才会加载本文件。

## 模块职责

嵌入式 HTTP 服务器 + 路由 + 请求绑定 + 参数校验 + WebSocket + 文件上传 + CORS。依赖 summer-core。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summe.web.server` | HTTP 服务器：ServerSocketChannel + 虚拟线程(NIMA)，TLS 支持，请求分发 |
| `cn.jiebaba.summe.web.routing` | 路由：Router + RoutePattern（路径变量匹配） |
| `cn.jiebaba.summe.web.bind` | 请求绑定：HandlerMethodInvoker + 参数解析器 |
| `cn.jiebaba.summe.web.http` | HTTP 抽象：WebRequest/WebResponse/HttpStatus/MediaType/RawHttpRequest |
| `cn.jiebaba.summe.web.convert` | 消息转换：JsonMessageConverter（JSON ↔ 对象） |
| `cn.jiebaba.summe.web.validation` | 参数校验：@Valid + 约束注解（NotBlank/Email 等） |
| `cn.jiebaba.summe.web.websocket` | WebSocket：RFC 6455 握手+帧协议，@WebSocketEndpoint |
| `cn.jiebaba.summe.web.multipart` | 文件上传：multipart/form-data 解析，MultipartFile |
| `cn.jiebaba.summe.web.cors` | CORS：CorsFilter + CorsProperties |
| `cn.jiebaba.summe.web.sse` | SSE：SseEmitter（异步推送）+ SseEvent（帧模型），chunked 分块响应 |
| `cn.jiebaba.summe.web.filter` | 过滤器链：Filter/FilterChain |
| `cn.jiebaba.summe.web.support` | Web 路由注册 + 异常处理注册 |
| `cn.jiebaba.summe.web.annotation` | Web 注解：@RestController/@RestControllerAdvice/@RequestParam/@PathVariable/@RequestPart/@ResponseStatus |

## 模块约定

- **NIMA 模型**：参考 Helidon NIMA，使用阻塞式 ServerSocketChannel + 虚拟线程，每个请求一个虚拟线程
- **路由匹配**：Router 支持 path variable（如 `/api/user/{id}`），不支持正则路径
- **参数校验**：Validator 递归校验嵌套对象，校验失败返回 400 + 违规列表
- **gzip 压缩**：`server.compression.enabled`（默认关）+ `mime-types` + `min-response-size`，提交前按
  Accept-Encoding/Content-Type/长度判定，未满足或压缩失败回退明文
- **SSE**：handler 返回 `SseEmitter`（`cn.jiebaba.summer.web.sse`）即开启事件流；请求线程从内部队列
  取事件按 chunked 写出（`SseEvent` 完整帧/String data/其他 JSON data 混合编码），`text/event-stream`
  + `Cache-Control: no-cache`；complete/completeWithError/空闲超时结束流，客户端断开关闭连接
- **抽象优先**：关键接口（WebRequest/WebResponse/MessageConverter/HandlerMethodArgumentResolver）针对接口编程
