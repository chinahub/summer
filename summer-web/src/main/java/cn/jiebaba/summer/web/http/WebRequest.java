package cn.jiebaba.summer.web.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WebRequest {
    private final HttpMethod method;
    private final String path;
    private final Map<String, List<String>> queryParams;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    private final Map<String, String> pathVariables = new LinkedHashMap<>();

    public WebRequest(RawHttpRequest raw) {
        this.method = HttpMethod.from(raw.method());
        String target = raw.target();
        int q = target.indexOf('?');
        String rawPath = q < 0 ? target : target.substring(0, q);
        String rawQuery = q < 0 ? null : target.substring(q + 1);
        this.path = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
        this.queryParams = parseQuery(rawQuery);
        this.headers = raw.headers();
        this.body = raw.body();
    }

    private static Map<String, List<String>> parseQuery(String rawQuery) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return map;
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            key = URLDecoder.decode(key, StandardCharsets.UTF_8);
            value = URLDecoder.decode(value, StandardCharsets.UTF_8);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return map;
    }

    public HttpMethod method() { return method; }
    public String path() { return path; }

    public String header(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return values == null ? null : values.get(0);
    }
    public String contentType() { return header("Content-Type"); }

    public String query(String name) {
        List<String> values = queryParams.get(name);
        return values == null ? null : values.get(0);
    }
    public List<String> queryList(String name) {
        return queryParams.getOrDefault(name, List.of());
    }
    public Map<String, List<String>> queryParams() { return queryParams; }

    public Map<String, String> pathVariables() { return pathVariables; }
    public String pathVariable(String name) { return pathVariables.get(name); }
    public void putPathVariable(String name, String value) { pathVariables.put(name, value); }

    public byte[] bodyBytes() { return body; }
    public String body() { return new String(body, StandardCharsets.UTF_8); }

    public String remoteAddress() { return "tcp"; }
}
