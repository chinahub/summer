package cn.jiebaba.summer.web.convert;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public interface MessageConverter {
    boolean canRead(Class<?> type, Type genericType, String contentType);

    Object read(String body, Class<?> type, Type genericType);

    /**
     * 从请求体字节直接读取并绑定到目标类型，默认实现回退到字符串读取；
     * 支持流式绑定的实现（如 JSON）可覆写以避免中间字符串与通用树。
     *
     * @param body        请求体字节
     * @param type        目标原始类型
     * @param genericType 目标泛型类型
     * @return 绑定后的对象
     */
    default Object read(byte[] body, Class<?> type, Type genericType) {
        return read(new String(body, StandardCharsets.UTF_8), type, genericType);
    }

    boolean canWrite(Class<?> type, String contentType);

    byte[] write(Object value, String contentType);

    String defaultContentType();
}
