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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理器方法调用器：在首次调用时缓存每个方法的参数元数据与绑定策略，
 * 并以 {@link MethodHandle} 折叠调用，避免每请求重复执行 getParameters、
 * getGenericParameterTypes、getAnnotation 与 makeAccessible。
 */
public final class HandlerMethodInvoker {
    private final ApplicationContext context;
    private final MessageConverter converter;
    private final List<HandlerMethodArgumentResolver> resolvers;
    private final ConcurrentHashMap<Method, CachedMethod> cache = new ConcurrentHashMap<>();

    public HandlerMethodInvoker(ApplicationContext context, MessageConverter converter) {
        this.context = context;
        this.converter = converter;
        this.resolvers = new ArrayList<>(context.getBeansOfType(HandlerMethodArgumentResolver.class).values());
    }

    /**
     * 调用处理器方法：从缓存取出参数绑定策略与折叠调用句柄，逐参绑定后派发调用。
     */
    public Object invoke(RouteMatch match, WebRequest request, WebResponse response) throws Exception {
        Method method = match.mapping().handlerMethod();
        Object bean = match.mapping().handlerBean();
        CachedMethod cm = cache.computeIfAbsent(method, this::build);
        Object[] args = new Object[cm.params.length + 1];
        args[0] = bean;
        for (int i = 0; i < cm.params.length; i++) {
            args[i + 1] = bind(cm.params[i], match, request, response);
        }
        return cm.invoke(args);
    }

    /** 首次调用时构建方法缓存项：一次性解析全部参数的绑定策略与折叠调用句柄。 */
    private CachedMethod build(Method method) {
        Parameter[] parameters = method.getParameters();
        Type[] genericTypes = method.getGenericParameterTypes();
        ParamSpec[] specs = new ParamSpec[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            specs[i] = buildSpec(parameters[i], genericTypes[i]);
        }
        return new CachedMethod(method, specs);
    }

    /**
     * 预计算单个参数的绑定策略：先匹配已注册解析器，再按类型与注解分派，
     * 缓存名称、必填、默认值与是否校验等元数据，供请求期直接使用。
     */
    private ParamSpec buildSpec(Parameter param, Type genericType) {
        for (HandlerMethodArgumentResolver resolver : resolvers) {
            if (resolver.supportsParameter(param)) {
                return new ParamSpec(Kind.RESOLVER, param, genericType, resolver, null, false, null, false);
            }
        }
        Class<?> type = param.getType();
        if (type == WebRequest.class) return new ParamSpec(Kind.WEB_REQUEST, param, genericType, null, null, false, null, false);
        if (type == WebResponse.class) return new ParamSpec(Kind.WEB_RESPONSE, param, genericType, null, null, false, null, false);
        if (type == ApplicationContext.class) return new ParamSpec(Kind.APP_CONTEXT, param, genericType, null, null, false, null, false);
        if (type == Environment.class) return new ParamSpec(Kind.ENVIRONMENT, param, genericType, null, null, false, null, false);

        PathVariable pathVar = param.getAnnotation(PathVariable.class);
        if (pathVar != null) {
            String name = nameOf(pathVar.value(), pathVar.name(), param);
            return new ParamSpec(Kind.PATH_VARIABLE, param, genericType, null, name, pathVar.required(), null, false);
        }
        RequestParam query = param.getAnnotation(RequestParam.class);
        if (query != null) {
            String name = nameOf(query.value(), query.name(), param);
            return new ParamSpec(Kind.REQUEST_PARAM, param, genericType, null, name, query.required(), query.defaultValue(), false);
        }
        RequestHeader header = param.getAnnotation(RequestHeader.class);
        if (header != null) {
            String name = nameOf(header.value(), header.name(), param);
            return new ParamSpec(Kind.REQUEST_HEADER, param, genericType, null, name, header.required(), header.defaultValue(), false);
        }
        RequestBody body = param.getAnnotation(RequestBody.class);
        if (body != null) {
            return new ParamSpec(Kind.REQUEST_BODY, param, genericType, null, null, body.required(), null, param.isAnnotationPresent(Valid.class));
        }
        if (isSimpleType(type)) {
            return new ParamSpec(Kind.SIMPLE_QUERY, param, genericType, null, param.getName(), false, null, false);
        }
        return new ParamSpec(Kind.MODEL_ATTRIBUTE, param, genericType, null, param.getName(), false, null, param.isAnnotationPresent(Valid.class));
    }

    /** 依据缓存的绑定策略绑定单个参数值。 */
    private Object bind(ParamSpec spec, RouteMatch match, WebRequest request, WebResponse response) throws Exception {
        switch (spec.kind) {
            case RESOLVER -> { return spec.resolver.resolveArgument(spec.param, spec.genericType, match, request, response); }
            case WEB_REQUEST -> { return request; }
            case WEB_RESPONSE -> { return response; }
            case APP_CONTEXT -> { return context; }
            case ENVIRONMENT -> { return context.getEnvironment(); }
            case PATH_VARIABLE -> {
                String value = match.pathVariables().get(spec.name);
                if (value == null) {
                    if (spec.required) throw new HandlerException("Missing path variable: " + spec.name);
                    return null;
                }
                return Environment.convert(value, spec.type);
            }
            case REQUEST_PARAM -> { return bindRequestParam(spec, request); }
            case REQUEST_HEADER -> {
                String value = request.header(spec.name);
                if (value == null) value = (spec.defaultValue != null && !spec.defaultValue.isEmpty()) ? spec.defaultValue : null;
                if (value == null) {
                    if (spec.required) throw new HandlerException("Missing request header: " + spec.name);
                    return null;
                }
                return Environment.convert(value, spec.type);
            }
            case REQUEST_BODY -> {
                byte[] raw = request.bodyBytes();
                if (raw == null || raw.length == 0) {
                    if (spec.required) throw new HandlerException("Request body is required");
                    return null;
                }
                Object bound = converter.read(raw, spec.type, spec.genericType);
                if (spec.valid && bound != null) Validator.requireValid(bound);
                return bound;
            }
            case SIMPLE_QUERY -> {
                String value = request.query(spec.name);
                if (value == null) return null;
                return Environment.convert(value, spec.type);
            }
            case MODEL_ATTRIBUTE -> {
                Object model = bindModelAttribute(spec.name, spec.type, request);
                if (spec.valid && model != null) Validator.requireValid(model);
                return model;
            }
            default -> { return null; }
        }
    }

    /** 绑定 {@link RequestParam}：按数组、集合与单值三种情形从查询参数取值，支持默认值与必填校验。 */
    @SuppressWarnings("unchecked")
    private Object bindRequestParam(ParamSpec spec, WebRequest request) {
        String name = spec.name;
        String defaultValue = spec.defaultValue;
        boolean hasDefault = defaultValue != null && !defaultValue.isEmpty();
        Class<?> type = spec.type;
        Type genericType = spec.genericType;
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
            if (spec.required) throw new HandlerException("Missing request parameter: " + name);
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

    /** 参数绑定策略种类。 */
    private enum Kind {
        RESOLVER, WEB_REQUEST, WEB_RESPONSE, APP_CONTEXT, ENVIRONMENT,
        PATH_VARIABLE, REQUEST_PARAM, REQUEST_HEADER, REQUEST_BODY, SIMPLE_QUERY, MODEL_ATTRIBUTE
    }

    /** 缓存的单个参数绑定描述：含种类、原始参数、泛型类型、解析器与预计算的名称/必填/默认值/校验。 */
    private static final class ParamSpec {
        final Kind kind;
        final Parameter param;
        final Type genericType;
        final Class<?> type;
        final HandlerMethodArgumentResolver resolver;
        final String name;
        final boolean required;
        final String defaultValue;
        final boolean valid;

        ParamSpec(Kind kind, Parameter param, Type genericType, HandlerMethodArgumentResolver resolver,
                  String name, boolean required, String defaultValue, boolean valid) {
            this.kind = kind;
            this.param = param;
            this.genericType = genericType;
            this.type = param.getType();
            this.resolver = resolver;
            this.name = name;
            this.required = required;
            this.defaultValue = defaultValue;
            this.valid = valid;
        }
    }

    /** 缓存的处理器方法：参数策略数组与折叠调用句柄，调用失败回退到反射。 */
    private static final class CachedMethod {
        final Method method;
        final ParamSpec[] params;
        final MethodHandle spreader; // 为 null 时回退到 Method.invoke

        CachedMethod(Method method, ParamSpec[] params) {
            this.method = method;
            this.params = params;
            this.spreader = buildSpreader(method);
        }

        /**
         * 构造折叠调用句柄：将方法句柄归一化为 {@code (Object[])Object}，
         * 使调用期可用 {@code invokeExact} 直接派发；构造失败时返回 null 走反射回退。
         */
        private static MethodHandle buildSpreader(Method method) {
            if (Modifier.isStatic(method.getModifiers())) return null;
            try {
                ReflectionUtils.makeAccessible(method);
                MethodHandle handle = MethodHandles.lookup().unreflect(method);
                MethodType type = handle.type();
                handle = handle.asType(type.changeReturnType(Object.class));
                return handle.asSpreader(Object[].class, type.parameterCount());
            } catch (Throwable t) {
                return null;
            }
        }

        /**
         * 调用处理器：优先用折叠句柄的 {@code invokeExact} 快速派发，失败回退 {@link Method#invoke}；
         * 异常语义保持与原反射一致——运行期异常原样抛出，其余封装为 {@link HandlerException}。
         */
        Object invoke(Object[] args) throws Exception {
            if (spreader != null) {
                try {
                    return (Object) spreader.invokeExact(args);
                } catch (RuntimeException re) {
                    throw re;
                } catch (Throwable t) {
                    throw new HandlerException("Handler invocation failed: " + method, t);
                }
            }
            try {
                return method.invoke(args[0], java.util.Arrays.copyOfRange(args, 1, args.length));
            } catch (ReflectiveOperationException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (cause instanceof RuntimeException re) throw re;
                throw new HandlerException("Handler invocation failed: " + method, cause);
            }
        }
    }
}
