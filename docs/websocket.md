# WebSocket（summer-web）

基于 `ServerSocket` + 虚拟线程实现的纯 JDK WebSocket 服务端，遵循 RFC 6455，零第三方依赖。握手复用 HTTP 服务器，握手后同一 TCP 连接转为全双工帧通信。

## 注解

| 注解 | 作用域 | 说明 |
| --- | --- | --- |
| `@WebSocketEndpoint("/ws/echo")` | 类 | 声明 WebSocket 端点（元标注 `@Component`，自动注册为 bean） |
| `@OnOpen` | 方法 | 连接建立时回调 |
| `@OnMessage` | 方法 | 收到文本/二进制消息时回调 |
| `@OnClose` | 方法 | 连接关闭时回调 |
| `@OnError` | 方法 | 连接异常时回调（可注入 `Throwable`） |

## WebSocketSession

```java
public final class WebSocketSession {
    public String id();
    public void sendText(String message);
    public void sendBinary(byte[] data);
    public void sendPing(byte[] data);
    public void close(CloseReason reason);
    public boolean isClosed();
    void runLoop();  // 框架内部调用，阻塞读帧
}
```

回调方法可注入的参数（按需选择，框架按类型匹配）：
- `WebSocketSession` — 当前会话
- `String` — 文本消息内容
- `byte[]` / `ByteBuffer` — 二进制消息内容
- `Throwable` — 异常（仅 `@OnError`）

## 示例

```java
@WebSocketEndpoint("/ws/echo")
public class EchoEndpoint {
    private static final Logger LOG = Logger.getLogger(EchoEndpoint.class.getName());
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(WebSocketSession session) {
        sessions.add(session);
        session.sendText("connected");
    }

    @OnMessage
    public void onMessage(WebSocketSession session, String message) {
        session.sendText("echo: " + message);
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        sessions.remove(session);
    }
}
```

## 运行模型

```
ServerSocket.accept() → 虚拟线程
    │
    ▼
RawHttpRequest.parse()
    │
    ├─ WebSocket Upgrade 请求?
    │   ├─ 是 → 101 握手 → WebSocketSession.runLoop()（同一虚拟线程阻塞读帧）
    │   │       │
    │   │       ├─ 帧解析（FIN/opcode/mask/payload-len/分片）
    │   │       ├─ 文本帧 → @OnMessage
    │   │       ├─ 二进制帧 → @OnMessage
    │   │       ├─ Ping → 自动回 Pong
    │   │       ├─ Close → 关闭连接 → @OnClose
    │   │       └─ 异常 → @OnError
    │   │
    │   └─ 否 → 走 RequestDispatcher（普通 HTTP）
    │
    ▼
```

- 每个 WebSocket 连接独占一个虚拟线程做阻塞读帧，不占平台线程；
- 服务器发送的帧不 masked（符合 RFC 6455），客户端发送的帧必须 masked（自动解码）；
- 支持大 payload（7/16/64 位长度）、ping/pong 心跳、close 帧；
- 单帧 payload 上限 16MB（防止内存溢出，可按需调整）。

## 帧协议（RFC 6455）

- **握手**：`Sec-WebSocket-Key` + magic GUID → SHA-1 → Base64 → `Sec-WebSocket-Accept`；
- **帧头**：FIN(1) + RSV(3) + opcode(4) + masked(1) + payload-len(7/16/64) + masking-key(0/4)；
- **opcode**：0x1 text、0x2 binary、0x8 close、0x9 ping、0xA pong、0x0 continuation；
- 客户端→服务端帧必须 masked，payload 用 4 字节掩码 XOR 解码。

## 模块要求

业务模块需 `opens` WebSocket 端点包给 `summer.web`（反射调用回调方法需要）：

```java
opens cn.jiebaba.summer.sample.websocket to summer.web;
```

## 验证

`WebSocketSmokeTest`（进程内，**5 项断言全过**）：
- 101 Switching Protocols 握手成功
- `Sec-WebSocket-Accept` 正确（SHA-1 + magic GUID）
- `@OnOpen` 发送 "connected" 消息
- echo 往返：发送 "hello summer" → 收到 "echo: hello summer"
- ping/pong 心跳正常
