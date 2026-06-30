package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.security.jwt.JsonReader;
import cn.jiebaba.summer.security.jwt.JsonWriter;

import java.util.Map;

/** Reuses the JWT module's minimal JSON reader/writer for login request/response bodies. */
final class LoginBodyParser {

    private LoginBodyParser() {}

    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        return (Map<String, Object>) JsonReader.read(json);
    }

    static String stringify(Map<String, Object> map) {
        return JsonWriter.write(map);
    }
}
