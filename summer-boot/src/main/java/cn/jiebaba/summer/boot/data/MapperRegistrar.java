package cn.jiebaba.summer.boot.data;

import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.scanner.ClassPathScanner;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.mapper.BaseMapper;
import cn.jiebaba.summer.data.mapper.MapperProxyFactory;
import cn.jiebaba.summer.data.mapper.MapperSupport;
import cn.jiebaba.summer.data.support.SqlExecutor;

import java.util.Set;

/**
 * 扫描用户声明的 {@link BaseMapper} 子接口，并为每个子接口注册一个
 * Bean 定义（带实例供给器），使 mapper 参与正常的依赖解析，
 * 可被 {@code @Autowired} 注入到 service 中。
 * 必须在 {@code context.refresh()} 之前调用。
 */
public final class MapperRegistrar {

    private MapperRegistrar() {}

    public static int registerDefinitions(DefaultApplicationContext context, Set<String> basePackages) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Set<Class<?>> candidates = ClassPathScanner.scan(basePackages, loader);
        int count = 0;
        for (Class<?> candidate : candidates) {
            if (!candidate.isInterface()) continue;
            if (!BaseMapper.class.isAssignableFrom(candidate)) continue;
            if (candidate == BaseMapper.class) continue;
            String beanName = DefaultApplicationContext.decapitalize(candidate.getSimpleName());
            BeanDefinition def = new BeanDefinition(beanName, candidate);
            def.setSynthetic(true);
            def.setInstanceSupplier(() -> createProxy(context, candidate));
            context.registerBeanDefinition(beanName, def);
            count++;
        }
        return count;
    }

    private static Object createProxy(DefaultApplicationContext context, Class<?> mapperInterface) {
        SqlExecutor executor = context.getBean(SqlExecutor.class);
        Dialect dialect;
        try { dialect = context.getBean(Dialect.class); } catch (Exception e) { dialect = Dialect.of(null); }
        MapperSupport<?> support = new MapperSupport<>(MapperProxyFactory.tableInfoFor(mapperInterface), executor, dialect);
        return MapperProxyFactory.create(mapperInterface, support);
    }
}
