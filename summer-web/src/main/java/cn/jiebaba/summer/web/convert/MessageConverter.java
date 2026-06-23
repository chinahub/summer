package cn.jiebaba.summer.web.convert;

import java.lang.reflect.Type;

public interface MessageConverter {
    boolean canRead(Class<?> type, Type genericType, String contentType);
    Object read(String body, Class<?> type, Type genericType);
    boolean canWrite(Class<?> type, String contentType);
    byte[] write(Object value, String contentType);
    String defaultContentType();
}
