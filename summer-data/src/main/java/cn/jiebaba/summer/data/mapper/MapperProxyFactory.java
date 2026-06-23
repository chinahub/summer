package cn.jiebaba.summer.data.mapper;

import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.metadata.MetadataParser;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.page.IPage;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Creates a JDK dynamic proxy implementing a user {@link BaseMapper} subinterface,
 * delegating every {@link BaseMapper} method to a shared {@link MapperSupport}.
 */
public final class MapperProxyFactory {

    private MapperProxyFactory() {}

    @SuppressWarnings("unchecked")
    public static <T> T create(Class<?> mapperInterface, MapperSupport<T> support) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> mapperInterface.getName() + "@proxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                };
            }
            return invoke(support, method, args);
        };
        return (T) Proxy.newProxyInstance(
                mapperInterface.getClassLoader(),
                new Class<?>[]{mapperInterface},
                handler);
    }

    @SuppressWarnings("unchecked")
    private static <T> Object invoke(MapperSupport<T> support, Method method, Object[] args) throws Throwable {
        return switch (method.getName()) {
            case "insert" -> support.insert((T) args[0]);
            case "deleteById" -> support.deleteById(args[0]);
            case "updateById" -> support.updateById((T) args[0]);
            case "selectById" -> support.selectById(args[0]);
            case "selectList" -> args == null || args.length == 0
                    ? support.selectList()
                    : support.selectList((AbstractWrapper<T, ?>) args[0]);
            case "selectOne" -> support.selectOne((AbstractWrapper<T, ?>) args[0]);
            case "selectCount" -> support.selectCount((AbstractWrapper<T, ?>) args[0]);
            case "selectPage" -> support.selectPage((IPage<T>) args[0],
                    (AbstractWrapper<T, ?>) args[1]);
            default -> throw new UnsupportedOperationException("Unsupported mapper method: " + method);
        };
    }

    public static Class<?> resolveEntityType(Class<?> mapperInterface) {
        java.lang.reflect.Type[] interfaces = mapperInterface.getGenericInterfaces();
        for (java.lang.reflect.Type t : interfaces) {
            if (t instanceof java.lang.reflect.ParameterizedType pt
                    && pt.getRawType() == BaseMapper.class) {
                java.lang.reflect.Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> c) return c;
            }
        }
        for (Class<?> parent : mapperInterface.getInterfaces()) {
            Class<?> resolved = resolveEntityType(parent);
            if (resolved != null) return resolved;
        }
        return null;
    }

    public static TableInfo tableInfoFor(Class<?> mapperInterface) {
        Class<?> entity = resolveEntityType(mapperInterface);
        if (entity == null) {
            throw new IllegalStateException("Cannot resolve entity type for mapper " + mapperInterface.getName());
        }
        return MetadataParser.parse(entity);
    }
}
