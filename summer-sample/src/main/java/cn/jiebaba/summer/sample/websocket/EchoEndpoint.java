package cn.jiebaba.summer.sample.websocket;

import cn.jiebaba.summer.web.websocket.OnClose;
import cn.jiebaba.summer.web.websocket.OnMessage;
import cn.jiebaba.summer.web.websocket.OnOpen;
import cn.jiebaba.summer.web.websocket.WebSocketEndpoint;
import cn.jiebaba.summer.web.websocket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@WebSocketEndpoint("/ws/echo")
public class EchoEndpoint {
    private static final Logger LOG = Logger.getLogger(EchoEndpoint.class.getName());
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @OnOpen
    public void onOpen(WebSocketSession session) {
        sessions.add(session);
        LOG.info("WebSocket opened: " + session.id() + " (total=" + sessions.size() + ")");
        session.sendText("connected");
    }

    @OnMessage
    public void onMessage(WebSocketSession session, String message) {
        LOG.info("WebSocket echo: " + message);
        session.sendText("echo: " + message);
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        sessions.remove(session);
        LOG.info("WebSocket closed: " + session.id() + " (total=" + sessions.size() + ")");
    }
}