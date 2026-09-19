package cn.jiebaba.summer.core.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ReflectionUtils {
    private ReflectionUtils() {}

    public static void makeAccessible(Constructor<?> ctor) {
        if (ctor != null) {
            try { ctor.setAccessible(true); } catch (SecurityException ignore) {}
        }
    }

    public static void makeAccessible(Method method) {
        if (method != null) {
            try { method.setAccessible(true); } catch (SecurityException ignore) {}
        }
    }

    public static void makeAccessible(Field field) {
        if (field != null) {
            try { field.setAccessible(true); } catch (SecurityException ignore) {}
        }
    }

    /** 收集类层级中所有声明字段，排除合成字段。 */
    public static List<Field> collectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!f.isSynthetic()) fields.add(f);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    public static Object invokeMethod(Method method, Object target, Object... args) {
        try {
            makeAccessible(method);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Failed to invoke " + method, cause);
        }
    }
}
