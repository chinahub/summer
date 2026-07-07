package cn.jiebaba.summer.web.routing;

import java.util.Map;
import java.util.Optional;

/**
 * 将 URL 路径与模式匹配，例如 {@code /users/{id}/repos/{name}}。
 * 支持单段变量 {@code {var}}、单段通配符 {@code *} 以及末尾全捕获 {@code /**}。
 */
public final class RoutePattern {
    private final String pattern;
    private final String[] segments;
    private final boolean catchAll;

    public RoutePattern(String pattern) {
        this.pattern = normalize(pattern);
        String trimmed = this.pattern;
        if (trimmed.endsWith("/**")) {
            this.catchAll = true;
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        } else {
            this.catchAll = false;
        }
        this.segments = trimmed.isEmpty() ? new String[0] : trimmed.split("/");
    }

    public String pattern() { return pattern; }

    /** 模式分段（已规范化；catch-all 模式不含末尾 {@code /**}）。 */
    String[] segments() { return segments; }

    /** 是否为末尾全捕获模式（{@code /**}）。 */
    boolean catchAll() { return catchAll; }

    /** 将请求路径规范化并分段，供路由索引匹配使用。 */
    static String[] requestSegments(String requestPath) {
        String normalized = normalize(requestPath);
        return normalized.isEmpty() ? new String[0] : normalized.split("/");
    }

    /**
     * 将请求路径与本模式匹配：逐段比对，支持 {@code {var}} 变量捕获与 {@code *} 通配符，
     * 命中时返回路径变量映射，否则返回空。catch-all 模式允许请求路径更长。
     *
     * @param requestPath 规范化后的请求路径
     * @return 命中时包含路径变量的 {@link Optional}，否则为空
     */
    public Optional<Map<String, String>> match(String requestPath) {
        String normalized = normalize(requestPath);
        if (catchAll && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String[] req = normalized.isEmpty() ? new String[0] : normalized.split("/");
        if (catchAll) {
            if (req.length < segments.length) return Optional.empty();
        } else if (req.length != segments.length) {
            return Optional.empty();
        }
        Map<String, String> variables = new java.util.LinkedHashMap<>();
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            String value = req[i];
            if (seg.startsWith("{") && seg.endsWith("}")) {
                String name = seg.substring(1, seg.length() - 1);
                variables.put(name, value);
            } else if (seg.equals("*")) {
                // 通配符，不捕获变量
            } else if (!seg.equals(value)) {
                return Optional.empty();
            }
        }
        return Optional.of(variables);
    }

    /** 特异性分值：字面量优先于变量，更长的模式优先于更短的模式。 */
    public int specificity() {
        int score = segments.length * 10;
        for (String seg : segments) {
            if (!seg.startsWith("{") && !seg.equals("*")) score += 5;
        }
        return score;
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) return "";
        String p = path;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (!p.startsWith("/")) p = "/" + p;
        if (p.equals("/")) return "";
        return p.substring(1);
    }

    @Override
    public String toString() { return pattern; }
}
