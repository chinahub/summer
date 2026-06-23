package cn.jiebaba.summer.web.websocket;

import java.lang.reflect.Method;

/** Metadata for a registered @WebSocketEndpoint bean. */
public final class WebSocketEndpointInfo {
    private final String path;
    private final Object bean;
    private final Method onOpen;
    private final Method onMessage;
    private final Method onClose;
    private final Method onError;

    public WebSocketEndpointInfo(String path, Object bean, Method onOpen, Method onMessage,
                                 Method onClose, Method onError) {
        this.path = path;
        this.bean = bean;
        this.onOpen = onOpen;
        this.onMessage = onMessage;
        this.onClose = onClose;
        this.onError = onError;
    }

    public String path() { return path; }
    public Object bean() { return bean; }
    public Method onOpen() { return onOpen; }
    public Method onMessage() { return onMessage; }
    public Method onClose() { return onClose; }
    public Method onError() { return onError; }
}