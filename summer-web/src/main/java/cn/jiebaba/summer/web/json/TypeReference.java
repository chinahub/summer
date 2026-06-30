package cn.jiebaba.summer.web.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures a generic type at compile time via an anonymous subclass, reifying it
 * at runtime — the JDK erases generics otherwise. Use it to parse parameterized
 * types such as {@code List<User>}:
 * <pre>{@code
 * List<User> users = Json.parse(json, new TypeReference<List<User>>() {});
 * }</pre>
 */
public abstract class TypeReference<T> {

    private final Type type;

    protected TypeReference() {
        Type superClass = getClass().getGenericSuperclass();
        if (!(superClass instanceof ParameterizedType pt)) {
            throw new IllegalArgumentException(
                    "TypeReference must be instantiated with a type parameter, e.g. new TypeReference<List<User>>(){}");
        }
        this.type = pt.getActualTypeArguments()[0];
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "TypeReference<" + type.getTypeName() + ">";
    }
}
