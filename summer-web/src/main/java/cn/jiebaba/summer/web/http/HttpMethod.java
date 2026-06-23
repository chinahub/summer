package cn.jiebaba.summer.web.http;

public enum HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE;

    public static HttpMethod from(String method) {
        if (method == null) return GET;
        try {
            return HttpMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GET;
        }
    }
}
