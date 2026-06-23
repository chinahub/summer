package cn.jiebaba.summer.web.websocket;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import cn.jiebaba.summer.core.annotation.Component;
import java.lang.annotation.Target;

/** Marks a class as a WebSocket server endpoint mapped to the given path. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@Documented
public @interface WebSocketEndpoint {
    /** URI path, e.g. {@code "/ws/chat"}. */
    String value();
}