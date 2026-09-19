package cn.jiebaba.summer.core.context;

import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link cn.jiebaba.summer.core.annotation.ConfigurationProperties @ConfigurationProperties}
 * 的字段绑定器：按前缀将配置键绑定到 Bean 字段（驼峰字段名转 kebab-case 键）。
 * 支持 String/基本类型及包装/枚举、List（逗号分隔或 [i] 索引）、Map&lt;String,String&gt;
 * 与嵌套 POJO（递归，深度上限 8）。
 */
final class PropertiesBinder {

    private static final int MAX_NESTING_DEPTH = 8;

    private PropertiesBinder() {}

    /** 将 prefix 下全部配置键绑定到 bean 的非静态非 final 字段（type 为原始声明类型，非 AOP 代理类）。 */
    static void bind(Object bean, Class<?> type, String prefix, Environment env) {
        bindInto(type, bean, prefix, env, 0);
    }

    private static void bindInto(Class<?> type, Object bean, String prefix, Environment env, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new BeansException("ConfigurationProperties nesting deeper than " + MAX_NESTING_DEPTH
                    + " at prefix '" + prefix + "' (possible circular reference)");
        }
        for (Field f : ReflectionUtils.collectFields(type)) {
            int mods = f.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) continue;
            String key = prefix + "." + kebab(f.getName());
            Object value;
            try {
                value = resolveValue(env, key, f.getType(), f.getGenericType(), depth);
            } catch (RuntimeException e) {
                throw new BeansException("Failed to bind property '" + key + "' to field " + type.getName()
                        + "." + f.getName() + ": " + e.getMessage(), e);
            }
            if (value == null) continue;
            ReflectionUtils.makeAccessible(f);
            try {
                f.set(bean, value);
            } catch (IllegalAccessException e) {
                throw new BeansException("Failed to set property field " + type.getName() + "." + f.getName(), e);
            }
        }
    }

    /** 解析单个字段的绑定值：简单类型走配置与转换，List/Map/嵌套 POJO 走专用分支；无配置返回 null。 */
    private static Object resolveValue(Environment env, String key, Class<?> type, Type generic, int depth) {
        if (isSimple(type)) {
            String raw = env.getProperty(key);
            return raw == null ? null : Environment.convert(raw, type);
        }
        if (type == List.class) {
            return resolveList(env, key, elementType(generic), depth);
        }
        if (type == Map.class) {
            return resolveMap(env, key);
        }
        if (isBindablePojo(type) && hasSubKeys(env, key)) {
            Object nested;
            try {
                var ctor = type.getDeclaredConstructor();
                ctor.setAccessible(true);
                nested = ctor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new BeansException("Nested @ConfigurationProperties type '" + type.getName()
                        + "' requires a no-arg constructor", e);
            }
            bindInto(type, nested, key, env, depth + 1);
            return nested;
        }
        return null;
    }

    /** List 绑定：优先逗号分隔单键；否则收集 key[0]、key[1]… 连续索引键；无任何配置返回 null（保持字段默认值）。 */
    private static Object resolveList(Environment env, String key, Class<?> elementType, int depth) {
        Class<?> element = elementType == null ? String.class : elementType;
        List<Object> out = new ArrayList<>();
        if (hasSubKeys(env, key) && isBindablePojo(element) && depth < MAX_NESTING_DEPTH) {
            // 元素为嵌套 POJO：按 key[0].field / key[1].field 形式绑定
            int i = 0;
            while (hasSubKeys(env, key + "[" + i + "]")) {
                Object item;
                try {
                    var ctor = element.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    item = ctor.newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new BeansException("Nested list element '" + element.getName()
                            + "' requires a no-arg constructor", e);
                }
                bindInto(element, item, key + "[" + i + "]", env, depth + 2);
                out.add(item);
                i++;
            }
            return out.isEmpty() ? null : out;
        }
        String inline = env.getProperty(key);
        if (inline != null && !inline.isBlank()) {
            for (String item : inline.split(",")) {
                if (!item.isBlank()) out.add(Environment.convert(item.trim(), element));
            }
            return out;
        }
        int i = 0;
        while (true) {
            String indexed = env.getProperty(key + "[" + i + "]");
            if (indexed == null) break;
            out.add(Environment.convert(indexed.trim(), element));
            i++;
        }
        return out.isEmpty() ? null : out;
    }

    /** Map&lt;String,String&gt; 绑定：前缀下所有子键（子键不再展开）。 */
    private static Object resolveMap(Environment env, String key) {
        Map<String, String> out = new LinkedHashMap<>();
        String dotPrefix = key + ".";
        for (Map.Entry<String, String> e : env.all().entrySet()) {
            if (e.getKey().startsWith(dotPrefix) && !e.getKey().substring(dotPrefix.length()).isEmpty()) {
                out.put(e.getKey().substring(dotPrefix.length()), e.getValue());
            }
        }
        return out;
    }

    /** 驼峰字段名转 kebab-case 配置键（大写字母前加连字符并小写，首字母除外）。 */
    static String kebab(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isSimple(Class<?> type) {
        return type == String.class || type.isEnum()
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class
                || type == boolean.class || type == Boolean.class
                || type == char.class || type == Character.class;
    }

    private static boolean isBindablePojo(Class<?> type) {
        return !isSimple(type) && type != List.class && type != Map.class
                && !type.isArray() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }

    private static boolean hasSubKeys(Environment env, String key) {
        String dotPrefix = key + ".";
        for (String name : env.all().keySet()) {
            if (name.startsWith(dotPrefix)) return true;
        }
        return false;
    }

    private static Class<?> elementType(Type generic) {
        if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1
                && pt.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        return String.class;
    }
}
