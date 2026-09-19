package cn.jiebaba.summer.web.websocket;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import cn.jiebaba.summer.core.annotation.Component;
import java.lang.annotation.Target;

/** 将类标记为映射到指定路径的 WebSocket 服务端点。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
@Documented
public @interface WebSocketEndpoint {
    /** URI 路径，例如 {@code "/ws/chat"}。 */
    String value();
}
