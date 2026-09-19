package cn.jiebaba.summer.core.aop.bytecode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** 将 Java 反射类型/方法转换为 JVM 类型与方法描述符。 */
public final class Descriptor {

    private Descriptor() {}

    public static String of(Class<?> type) {
        if (type == void.class) return "V";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type.isArray()) return "[" + of(type.getComponentType());
        return "L" + type.getName().replace('.', '/') + ";";
    }

    public static String of(Class<?>[] paramTypes, Class<?> returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : paramTypes) sb.append(of(p));
        sb.append(")").append(of(returnType));
        return sb.toString();
    }

    public static String of(Method m) {
        return of(m.getParameterTypes(), m.getReturnType());
    }

    public static String of(Constructor<?> c) {
        return of(c.getParameterTypes(), void.class);
    }

    public static int slots(Class<?> type) {
        return (type == long.class || type == double.class) ? 2 : 1;
    }

    public static String internalName(Class<?> type) {
        if (type.isArray()) return of(type);
        return type.getName().replace('.', '/');
    }
}
