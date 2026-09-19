package cn.jiebaba.summer.core.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 通过匿名子类在编译期捕获泛型类型，并在运行期将其具现化——否则 JDK 会擦除泛型。
 * 用于解析诸如 {@code List<User>} 的参数化类型：
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
