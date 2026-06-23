package cn.jiebaba.summer.web.convert;

import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.json.Json;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public final class JsonMessageConverter implements MessageConverter {

    @Override
    public boolean canRead(Class<?> type, Type genericType, String contentType) {
        return true;
    }

    @Override
    public Object read(String body, Class<?> type, Type genericType) {
        return Json.bind(Json.parse(body), type, genericType);
    }

    @Override
    public boolean canWrite(Class<?> type, String contentType) {
        return true;
    }

    @Override
    public byte[] write(Object value, String contentType) {
        return Json.stringify(value).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String defaultContentType() {
        return MediaType.APPLICATION_JSON_UTF8;
    }
}
