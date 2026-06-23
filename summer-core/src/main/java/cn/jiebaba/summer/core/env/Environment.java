package cn.jiebaba.summer.core.env;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class Environment {
    public static final String DEFAULT_PROFILE = "default";

    private final Properties properties = new Properties();

    public Environment() {
        // lowest priority: system properties first then environment variables then app properties
        Properties app = new Properties();
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                app.load(in);
            }
        } catch (IOException e) {
            // ignore missing/invalid application.properties
        }
        for (String name : app.stringPropertyNames()) {
            properties.setProperty(name, app.getProperty(name));
        }
        // also load application.yml (if present) and flatten into dotted keys
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("application.yml")) {
            if (in != null) {
                String yml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                java.util.Map<String, Object> tree = YamlParser.parse(yml);
                for (java.util.Map.Entry<String, String> e : YamlParser.flatten(tree).entrySet()) {
                    if (!properties.containsKey(e.getKey())) {
                        properties.setProperty(e.getKey(), e.getValue());
                    }
                }
            }
        } catch (IOException e) {
            // ignore missing/invalid application.yml
        }
        // environment variables override: SERVER_PORT -> server.port style mapping
        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> e : env.entrySet()) {
            String key = relaxedKey(e.getKey());
            if (key != null) {
                properties.setProperty(key, e.getValue());
            }
        }
        // system properties win
        Properties sys = System.getProperties();
        for (String name : sys.stringPropertyNames()) {
            properties.setProperty(name, sys.getProperty(name));
        }
    }

    private static String relaxedKey(String envName) {
        if (envName == null || envName.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(envName.length() + 4);
        for (int i = 0; i < envName.length(); i++) {
            char c = envName.charAt(i);
            if (c == '_') {
                sb.append('.');
            } else if (c == '.') {
                // keep dots from system-style names untouched
                sb.append(c);
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public <T> T getProperty(String key, Class<T> targetType) {
        return getProperty(key, targetType, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        return (T) convert(value, targetType);
    }

    public boolean containsProperty(String key) {
        return properties.containsKey(key);
    }

    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }
        return map;
    }

    /** Resolve ${key} and ${key:default} placeholders inside the given text. */
    public String resolvePlaceholders(String text) {
        if (text == null) return null;
        return resolvePlaceholders(text, 0);
    }

    private String resolvePlaceholders(String text, int depth) {
        // Prevent infinite recursion from circular references (max 5 levels)
        if (depth >= 5) return text;
        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                int end = text.indexOf('}', i + 2);
                if (end < 0) {
                    result.append(c);
                    i++;
                    continue;
                }
                String expr = text.substring(i + 2, end);
                String key;
                String defaultValue = null;
                int colon = expr.indexOf(':');
                if (colon >= 0) {
                    key = expr.substring(0, colon).trim();
                    defaultValue = expr.substring(colon + 1).trim();
                } else {
                    key = expr.trim();
                }
                String value = properties.getProperty(key);
                if (value == null) {
                    value = defaultValue;
                }
                if (value != null) {
                    // Recursively resolve placeholders in the value itself for nesting support
                    String resolved = resolvePlaceholders(value, depth + 1);
                    result.append(resolved);
                } else {
                    result.append("${").append(expr).append("}");
                }
                i = end + 1;
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    /** Coerce a raw string value to the target type. */
    @SuppressWarnings("unchecked")
    public static Object convert(String value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType == String.class || targetType == Object.class || targetType == CharSequence.class) {
            return value;
        }
        if (targetType == int.class || targetType == Integer.class) return Integer.decode(value);
        if (targetType == long.class || targetType == Long.class) return Long.decode(value);
        if (targetType == short.class || targetType == Short.class) return Short.decode(value);
        if (targetType == byte.class || targetType == Byte.class) return Byte.decode(value);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(value);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
        if (targetType == char.class || targetType == Character.class) {
            return value.isEmpty() ? '\0' : value.charAt(0);
        }
        if (targetType.isEnum()) {
            @SuppressWarnings("rawtypes")
            Class enumType = targetType;
            @SuppressWarnings("unchecked")
            Object constant = Enum.valueOf(enumType, value);
            return constant;
        }
        return value;
    }
}
