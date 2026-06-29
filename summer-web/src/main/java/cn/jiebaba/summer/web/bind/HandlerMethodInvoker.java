package cn.jiebaba.summer.web.bind;

import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.util.ReflectionUtils;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestHeader;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.convert.MessageConverter;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.validation.Valid;
import cn.jiebaba.summer.web.validation.Validator;
import cn.jiebaba.summer.web.routing.RouteMatch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class HandlerMethodInvoker {
    private final ApplicationContext context;
    private final MessageConverter converter;
    private final List<HandlerMethodArgumentResolver> resolvers;

    public HandlerMethodInvoker(ApplicationContext context, MessageConverter converter) {
        this.context = context;
        this.converter = converter;
        this.resolvers = new ArrayList<>(context.getBeansOfType(HandlerMethodArgumentResolver.class).values());
    }

    public Object invoke(RouteMatch match, WebRequest request, WebResponse response) throws Exception {
        Method method = match.mapping().handlerMethod();
        Object bean = match.mapping().handlerBean();
        Parameter[] params = method.getParameters();
        Type[] genericTypes = method.getGenericParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = bindParameter(params[i], genericTypes[i], match, request, response);
        }
        ReflectionUtils.makeAccessible(method);
        try {
            return method.invoke(bean, args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new HandlerException("Handler invocation failed: " + method, cause);
        }
    }

    private Object bindParameter(Parameter param, Type genericType, RouteMatch match,
                                 WebRequest request, WebResponse response) throws Exception {
        for (HandlerMethodArgumentResolver resolver : resolvers) {
            if (resolver.supportsParameter(param)) {
                return resolver.resolveArgument(param, genericType, match, request, response);
            }
        }
        Class<?> type = param.getType();
        if (type == WebRequest.class) return request;
        if (type == WebResponse.class) return response;
        if (type == ApplicationContext.class) return context;
        if (type == Environment.class) return context.getEnvironment();

        PathVariable pathVar = param.getAnnotation(PathVariable.class);
        if (pathVar != null) {
            String name = nameOf(pathVar.value(), pathVar.name(), param);
            String value = match.pathVariables().get(name);
            if (value == null) {
                if (pathVar.required()) throw new HandlerException("Missing path variable: " + name);
                return null;
            }
            return Environment.convert(value, type);
        }

        RequestParam query = param.getAnnotation(RequestParam.class);
        if (query != null) {
            return bindRequestParam(query, param, type, genericType, request);
        }

        RequestHeader header = param.getAnnotation(RequestHeader.class);
        if (header != null) {
            String name = nameOf(header.value(), header.name(), param);
            String value = request.header(name);
            if (value == null) value = header.defaultValue().isEmpty() ? null : header.defaultValue();
            if (value == null) {
                if (header.required()) throw new HandlerException("Missing request header: " + name);
                return null;
            }
            return Environment.convert(value, type);
        }

        RequestBody body = param.getAnnotation(RequestBody.class);
        if (body != null) {
            String raw = request.body();
            if (raw == null || raw.isEmpty()) {
                if (body.required()) throw new HandlerException("Request body is required");
                return null;
            }
            Object bound = converter.read(raw, type, genericType);
            validateIfValid(param, bound);
            return bound;
        }

        // unannotated simple type -> bind from query parameter by name
        if (isSimpleType(type)) {
            String value = request.query(param.getName());
            if (value == null) return null;
            return Environment.convert(value, type);
        }

        // unannotated complex type -> bind model attributes from query parameters
        Object model = bindModelAttribute(param.getName(), type, request);
        validateIfValid(param, model);
        return model;
    }

    @SuppressWarnings("unchecked")
    private Object bindRequestParam(RequestParam query, Parameter param, Class<?> type, Type genericType,
                                    WebRequest request) {
        String name = nameOf(query.value(), query.name(), param);
        String defaultValue = query.defaultValue();
        boolean hasDefault = defaultValue != null && !defaultValue.isEmpty();
        if (type.isArray()) {
            Class<?> component = type.getComponentType();
            List<String> values = request.queryList(name);
            if (values.isEmpty() && hasDefault) values = List.of(defaultValue);
            Object array = java.lang.reflect.Array.newInstance(component, values.size());
            for (int i = 0; i < values.size(); i++) {
                java.lang.reflect.Array.set(array, i, Environment.convert(values.get(i), component));
            }
            return array;
        }
        if (Collection.class.isAssignableFrom(type)) {
            Class<?> elementType = Object.class;
            if (genericType instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0
                    && pt.getActualTypeArguments()[0] instanceof Class<?> c) {
                elementType = c;
            }
            List<String> values = request.queryList(name);
            if (values.isEmpty() && hasDefault) values = List.of(defaultValue);
            Collection<Object> col = new ArrayList<>();
            for (String v : values) col.add(Environment.convert(v, elementType));
            return col;
        }
        String value = request.query(name);
        if (value == null) value = hasDefault ? defaultValue : null;
        if (value == null) {
            if (query.required()) throw new HandlerException("Missing request parameter: " + name);
            return null;
        }
        return Environment.convert(value, type);
    }

    private Object bindModelAttribute(String prefix, Class<?> type, WebRequest request) {
        try {
            Object model = type.getDeclaredConstructor().newInstance();
            for (Field f : ReflectionUtils.collectFields(type)) {
                String key = prefix.isEmpty() ? f.getName() : prefix + "." + f.getName();
                String value = request.query(f.getName());
                if (value == null && request.query(key) != null) value = request.query(key);
                if (value == null) continue;
                if (isSimpleType(f.getType())) {
                    f.setAccessible(true);
                    f.set(model, Environment.convert(value, f.getType()));
                }
            }
            return model;
        } catch (ReflectiveOperationException e) {
            throw new HandlerException("Cannot bind model attribute " + type.getName(), e);
        }
    }

    private static void validateIfValid(Parameter param, Object value) {
        if (param.isAnnotationPresent(Valid.class) && value != null) {
            Validator.requireValid(value);
        }
    }

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type)
                || type == Boolean.class || type == Character.class || type.isEnum()
                || type == java.time.temporal.TemporalAccessor.class || type.getName().startsWith("java.time");
    }

    private static String nameOf(String value, String name, Parameter param) {
        if (value != null && !value.isEmpty()) return value;
        if (name != null && !name.isEmpty()) return name;
        return param.getName();
    }
}
