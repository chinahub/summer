package cn.jiebaba.summer.web.support;

import cn.jiebaba.summer.core.util.ReflectionUtils;
import cn.jiebaba.summer.web.annotation.ExceptionHandler;
import cn.jiebaba.summer.web.annotation.ResponseStatus;
import cn.jiebaba.summer.web.bind.HandlerException;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ExceptionHandlerRegistry {
    public record HandlerEntry(Object bean, Method method, Class<?> exceptionType, int status) {}

    private final List<HandlerEntry> handlers = new ArrayList<>();

    public void register(Object bean, Method method) {
        Class<?>[] types = method.getAnnotation(ExceptionHandler.class).value();
        int status = method.isAnnotationPresent(ResponseStatus.class)
                ? method.getAnnotation(ResponseStatus.class).value() : 0;
        if (types.length == 0) {
            for (Parameter p : method.getParameters()) {
                if (Throwable.class.isAssignableFrom(p.getType())) {
                    types = new Class<?>[]{p.getType()};
                    break;
                }
            }
        }
        if (types.length == 0) types = new Class<?>[]{Exception.class};
        for (Class<?> t : types) {
            handlers.add(new HandlerEntry(bean, method, t, status));
        }
    }

    public HandlerEntry resolve(Throwable throwable) {
        return handlers.stream()
                .filter(h -> h.exceptionType().isAssignableFrom(throwable.getClass()))
                .min(Comparator.comparingInt(h -> depth(throwable.getClass(), h.exceptionType())))
                .orElse(null);
    }

    private int depth(Class<?> thrown, Class<?> declared) {
        int depth = 0;
        Class<?> c = thrown;
        while (c != null && !declared.equals(c)) {
            c = c.getSuperclass();
            depth++;
        }
        return depth;
    }

    public Object invoke(HandlerEntry entry, Throwable throwable, WebRequest request, WebResponse response) {
        Method method = entry.method();
        Object[] args = new Object[method.getParameterCount()];
        for (int i = 0; i < method.getParameterCount(); i++) {
            Parameter p = method.getParameters()[i];
            if (Throwable.class.isAssignableFrom(p.getType())) args[i] = throwable;
            else if (p.getType() == WebRequest.class) args[i] = request;
            else if (p.getType() == WebResponse.class) args[i] = response;
            else args[i] = null;
        }
        if (entry.status() > 0) response.status(entry.status());
        ReflectionUtils.makeAccessible(method);
        try {
            return method.invoke(entry.bean(), args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new HandlerException("Exception handler failed: " + method, cause);
        }
    }

    public boolean isEmpty() { return handlers.isEmpty(); }
}
