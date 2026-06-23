package cn.jiebaba.summer.data.conditions;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

public final class LambdaUtils {
    private LambdaUtils() {}

    /** Resolve a property name from a getter-style method reference (getName -> name). */
    public static <T> String propertyName(SFunction<T, ?> function) {
        SerializedLambda lambda = serialized(function);
        String methodName = lambda.getImplMethodName();
        String property;
        if (methodName.startsWith("get")) {
            property = methodName.substring(3);
        } else if (methodName.startsWith("is")) {
            property = methodName.substring(2);
        } else {
            property = methodName;
        }
        if (property.isEmpty()) return property;
        return Character.toLowerCase(property.charAt(0)) + property.substring(1);
    }

    private static SerializedLambda serialized(SFunction<?, ?> function) {
        try {
            Method writeReplace = function.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replacement = writeReplace.invoke(function);
            if (replacement instanceof SerializedLambda sl) {
                return sl;
            }
            throw new IllegalStateException("Expected SerializedLambda but got " + replacement);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Not a serializable lambda reference: " + function
                    + ". Ensure the entity implements java.io.Serializable.", e);
        }
    }
}
