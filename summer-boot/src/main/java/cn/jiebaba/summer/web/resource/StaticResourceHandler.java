package cn.jiebaba.summer.web.resource;

import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.http.WebResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 静态资源处理器：把控制台前端等静态文件随 jar 一体化交付（路由优先、静态回退——
 * 请求未命中任何路由时才尝试静态资源，不与控制器争路径）。
 * <p>安全：拒绝含 {@code ..} / 反斜杠 / NUL 的路径（防目录穿越）；
 * 文件系统位置解析后校验仍在根目录内。仅响应 GET（HEAD 交由调用方语义处理）。
 */
public final class StaticResourceHandler {

    /** 扩展名 → Content-Type（未命中回退 application/octet-stream）。 */
    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("html", "text/html;charset=UTF-8"),
            Map.entry("htm", "text/html;charset=UTF-8"),
            Map.entry("css", "text/css;charset=UTF-8"),
            Map.entry("js", "text/javascript;charset=UTF-8"),
            Map.entry("mjs", "text/javascript;charset=UTF-8"),
            Map.entry("json", MediaType.APPLICATION_JSON_UTF8),
            Map.entry("map", MediaType.APPLICATION_JSON_UTF8),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("txt", "text/plain;charset=UTF-8"),
            Map.entry("xml", "application/xml;charset=UTF-8"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("wasm", "application/wasm"));

    private final StaticResourceProperties properties;
    private final ClassLoader classLoader;

    public StaticResourceHandler(StaticResourceProperties properties) {
        this(properties, StaticResourceHandler.class.getClassLoader());
    }

    public StaticResourceHandler(StaticResourceProperties properties, ClassLoader classLoader) {
        if (properties == null) throw new IllegalArgumentException("properties 不能为空");
        this.properties = properties;
        this.classLoader = classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /**
     * 尝试将请求路径作为静态资源响应：命中则写入响应并返回 true，
     * 未命中返回 false（调用方继续走 404/其他处理）。
     *
     * @param path     已去除 context-path 的请求路径（如 /assets/app.js）
     * @param response 当前响应
     * @throws IOException 资源读取失败
     */
    public boolean tryServe(HttpMethod method, String path, WebResponse response) throws IOException {
        if (!properties.enabled() || method != HttpMethod.GET) return false;
        if (path == null) return false;
        String rel = resolveRelativePath(path);
        if (rel == null) return false;
        for (String location : properties.locations()) {
            byte[] bytes = read(location, rel);
            if (bytes != null) {
                response.contentType(contentType(rel));
                response.body(bytes);
                return true;
            }
        }
        return false;
    }

    /** 路径 → 资源相对路径（/ 欢迎页映射；非法路径返回 null）。 */
    private String resolveRelativePath(String path) {
        String p = path;
        if (p.contains("..") || p.contains("\\") || p.indexOf('\0') >= 0) return null;
        if (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty() || p.endsWith("/")) {
            p = p + properties.welcome();
        }
        return p;
    }

    /** 从单个位置读取资源；不存在返回 null。 */
    private byte[] read(String location, String relPath) throws IOException {
        if (location.startsWith("classpath:")) {
            String root = location.substring("classpath:".length());
            while (root.startsWith("/")) root = root.substring(1);
            String resource = root.isEmpty() ? relPath : root + "/" + relPath;
            try (InputStream in = classLoader.getResourceAsStream(resource)) {
                return in == null ? null : in.readAllBytes();
            }
        }
        Path root = toPath(location).toAbsolutePath().normalize();
        Path file = root.resolve(relPath).normalize();
        if (!file.startsWith(root)) return null; // 目录穿越兜底
        if (!Files.isRegularFile(file)) return null;
        return Files.readAllBytes(file);
    }

    private static Path toPath(String location) {
        String p = location.startsWith("file:") ? location.substring("file:".length()) : location;
        return Path.of(p);
    }

    /** 按扩展名推断 Content-Type；未知回退二进制流。 */
    static String contentType(String relPath) {
        int dot = relPath.lastIndexOf('.');
        if (dot < 0 || dot == relPath.length() - 1) return MediaType.APPLICATION_OCTET_STREAM;
        String ext = relPath.substring(dot + 1).toLowerCase();
        return CONTENT_TYPES.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
    }
}
