package cn.jiebaba.summer.web.routing;

import java.util.Map;
import java.util.Optional;

/**
 * Matches a URL path against a pattern such as {@code /users/{id}/repos/{name}}.
 * Supports single-segment variables {@code {var}}, single-segment wildcards {@code *}
 * and a trailing catch-all {@code /**}.
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
                // wildcard, no capture
            } else if (!seg.equals(value)) {
                return Optional.empty();
            }
        }
        return Optional.of(variables);
    }

    /** Specificity score: literals beat variables, longer patterns beat shorter. */
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
