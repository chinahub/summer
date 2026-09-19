package cn.jiebaba.summer.web.convert;

import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.core.json.Json;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public final class JsonMessageConverter implements MessageConverter {

    @Override
    public boolean canRead(Class<?> type, Type genericType, String contentType) {
        return true;
    }

    @Override
    public Object read(String body, Class<?> type, Type genericType) {
        return read(body.getBytes(StandardCharsets.UTF_8), type, genericType);
    }

    /** 直接以字节流式绑定，不经中间字符串与通用树，热路径减分配。 */
    @Override
    public Object read(byte[] body, Class<?> type, Type genericType) {
        return Json.read(body, genericType);
    }

    @Override
    public boolean canWrite(Class<?> type, String contentType) {
        return true;
    }

    @Override
    public byte[] write(Object value, String contentType) {
        return Json.toUtf8Bytes(value);
    }

    @Override
    public String defaultContentType() {
        return MediaType.APPLICATION_JSON_UTF8;
    }
}
