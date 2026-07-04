package cn.jiebaba.summer.web.websocket;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 当 WebSocket 连接建立时调用。方法可接收一个 {@link WebSocketSession} 参数。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnOpen {
}
