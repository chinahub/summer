package cn.jiebaba.summer.web.websocket;

import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.scanner.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 扫描应用上下文中带 {@link WebSocketEndpoint} 注解的 Bean，
 * 收集其生命周期回调方法，并提供基于路径的查找。
 */
public final class WebSocketRegistry {

    private final Map<String, WebSocketEndpointInfo> endpoints = new LinkedHashMap<>();

    /**
     * 扫描上下文中带 {@link WebSocketEndpoint} 注解的 Bean，收集其
     * OnOpen/OnMessage/OnClose/OnError 回调方法，并按路径登记端点信息。
     */
    public void scan(ApplicationContext context) {
        Map<String, Object> candidates;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> found = (Map<String, Object>) (Map) context.getBeansWithAnnotation(WebSocketEndpoint.class);
            candidates = found;
        } catch (Exception e) {
            return;
        }
        for (var entry : candidates.entrySet()) {
            Object bean = entry.getValue();
            Class<?> type = bean.getClass();
            WebSocketEndpoint ann = AnnotationUtils.findAnnotation(type, WebSocketEndpoint.class);
            if (ann == null) continue;
            String path = normalizePath(ann.value());
            Method onOpen = findAnnotatedMethod(type, OnOpen.class);
            Method onMessage = findAnnotatedMethod(type, OnMessage.class);
            Method onClose = findAnnotatedMethod(type, OnClose.class);
            Method onError = findAnnotatedMethod(type, OnError.class);
            WebSocketEndpointInfo info = new WebSocketEndpointInfo(path, bean, onOpen, onMessage, onClose, onError);
            endpoints.put(path, info);
        }
    }

    public Optional<WebSocketEndpointInfo> match(String path) {
        return Optional.ofNullable(endpoints.get(normalizePath(path)));
    }

    public boolean hasEndpoints() {
        return !endpoints.isEmpty();
    }

    public Map<String, WebSocketEndpointInfo> endpoints() {
        return endpoints;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private static Method findAnnotatedMethod(Class<?> type, Class<? extends java.lang.annotation.Annotation> ann) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.isAnnotationPresent(ann)) return m;
        }
        Class<?> sup = type.getSuperclass();
        if (sup != null && sup != Object.class) {
            Method found = findAnnotatedMethod(sup, ann);
            if (found != null) return found;
        }
        return null;
    }
}
