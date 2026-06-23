package cn.jiebaba.summer.web.support;

import cn.jiebaba.summer.core.annotation.Controller;
import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.scanner.AnnotationUtils;
import cn.jiebaba.summer.web.annotation.DeleteMapping;
import cn.jiebaba.summer.web.annotation.ExceptionHandler;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PatchMapping;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.PutMapping;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RestControllerAdvice;
import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.routing.RouteMapping;
import cn.jiebaba.summer.web.routing.RoutePattern;
import cn.jiebaba.summer.web.routing.Router;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class WebRouteRegistrar {

    public record Registration(Router router, ExceptionHandlerRegistry exceptionHandlers) {}

    public static Registration build(ApplicationContext context) {
        Router router = new Router();
        ExceptionHandlerRegistry exceptions = new ExceptionHandlerRegistry();

        Map<String, Object> controllers = context.getBeansWithAnnotation(Controller.class);
        for (Object bean : controllers.values()) {
            registerController(router, bean);
        }

        Map<String, Object> advices = context.getBeansWithAnnotation(RestControllerAdvice.class);
        for (Object bean : advices.values()) {
            for (Method m : bean.getClass().getMethods()) {
                if (m.isAnnotationPresent(ExceptionHandler.class)) {
                    exceptions.register(bean, m);
                }
            }
        }

        router.sortBySpecificity();
        return new Registration(router, exceptions);
    }

    private static void registerController(Router router, Object bean) {
        Class<?> type = bean.getClass();
        String basePath = classLevelPath(type);
        for (Method method : type.getMethods()) {
            for (RouteDescriptor descriptor : descriptorsFor(method)) {
                String path = combine(basePath, descriptor.path);
                RouteMapping mapping = new RouteMapping(
                        descriptor.method, new RoutePattern(path), bean, method, descriptor.produces);
                router.register(mapping);
            }
        }
    }

    private static String classLevelPath(Class<?> type) {
        RequestMapping rm = type.getAnnotation(RequestMapping.class);
        if (rm == null) return "";
        return firstPath(rm.value(), rm.path());
    }

    private static List<RouteDescriptor> descriptorsFor(Method method) {
        List<RouteDescriptor> list = new ArrayList<>();
        RequestMapping rm = method.getAnnotation(RequestMapping.class);
        if (rm != null) {
            for (String path : paths(rm.value(), rm.path())) {
                HttpMethod http = rm.method().isEmpty() ? HttpMethod.GET : HttpMethod.from(rm.method());
                list.add(new RouteDescriptor(http, path, rm.produces()));
            }
        }
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) for (String p : paths(get.value(), get.path())) list.add(new RouteDescriptor(HttpMethod.GET, p, new String[0]));
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) for (String p : paths(post.value(), post.path())) list.add(new RouteDescriptor(HttpMethod.POST, p, new String[0]));
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) for (String p : paths(put.value(), put.path())) list.add(new RouteDescriptor(HttpMethod.PUT, p, new String[0]));
        DeleteMapping del = method.getAnnotation(DeleteMapping.class);
        if (del != null) for (String p : paths(del.value(), del.path())) list.add(new RouteDescriptor(HttpMethod.DELETE, p, new String[0]));
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) for (String p : paths(patch.value(), patch.path())) list.add(new RouteDescriptor(HttpMethod.PATCH, p, new String[0]));
        if (list.isEmpty()) {
            // also support meta-present mapping annotations on custom composed annotations
            if (AnnotationUtils.hasAnnotation(method, RequestMapping.class)) {
                RequestMapping meta = AnnotationUtils.findAnnotation(method, RequestMapping.class);
                for (String p : paths(meta.value(), meta.path())) {
                    HttpMethod http = meta.method().isEmpty() ? HttpMethod.GET : HttpMethod.from(meta.method());
                    list.add(new RouteDescriptor(http, p, meta.produces()));
                }
            }
        }
        return list;
    }

    private static String[] paths(String[] value, String[] path) {
        if (value != null && value.length > 0) return value;
        if (path != null && path.length > 0) return path;
        return new String[]{""};
    }

    private static String firstPath(String[] value, String[] path) {
        String[] all = paths(value, path);
        return all[0];
    }

    private static String combine(String base, String path) {
        if (path == null) path = "";
        if (base == null || base.isEmpty()) {
            if (path.isEmpty()) return "/";
            return path.startsWith("/") ? path : "/" + path;
        }
        String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (path.isEmpty()) return b.startsWith("/") ? b : "/" + b;
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    private record RouteDescriptor(HttpMethod method, String path, String[] produces) {}
}
