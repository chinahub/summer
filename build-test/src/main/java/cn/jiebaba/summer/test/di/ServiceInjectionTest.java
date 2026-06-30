package cn.jiebaba.summer.test.di;

import cn.jiebaba.summer.boot.data.MapperRegistrar;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.support.DataSourceFactory;
import cn.jiebaba.summer.data.support.SqlExecutor;

import java.util.Set;

/**
 * Verifies that {@link cn.jiebaba.summer.data.service.ServiceImpl} gets its
 * {@code baseMapper} auto-injected without any setter boilerplate in the subclass.
 */
public class ServiceInjectionTest {

    @Test
    void baseMapperAutoInjectedWithoutSetter() {
        Environment env = new Environment();
        DefaultApplicationContext context = new DefaultApplicationContext(
                ServiceInjectionTest.class.getClassLoader(), env, Set.of("cn.jiebaba.summer.test.di"));

        BeanDefinition sqlExecutor = new BeanDefinition("sqlExecutor", SqlExecutor.class);
        sqlExecutor.setInstanceSupplier(() -> new SqlExecutor(DataSourceFactory.lazyDummy()));
        context.registerBeanDefinition("sqlExecutor", sqlExecutor);

        BeanDefinition dialect = new BeanDefinition("dialect", Dialect.class);
        dialect.setInstanceSupplier(() -> Dialect.of("mysql"));
        context.registerBeanDefinition("dialect", dialect);

        MapperRegistrar.registerDefinitions(context, Set.of("cn.jiebaba.summer.test.di"));
        context.refresh();

        WidgetService service = context.getBean(WidgetService.class);
        Assert.assertNotNull(service.baseMapper(), "baseMapper should be auto-injected");
        Assert.assertTrue(service.baseMapper() instanceof WidgetMapper,
                "baseMapper should be a WidgetMapper proxy, got " + service.baseMapper());
    }
}
