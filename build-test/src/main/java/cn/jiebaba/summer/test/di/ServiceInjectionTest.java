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
 * 验证 {@link cn.jiebaba.summer.data.service.ServiceImpl} 的
 * {@code baseMapper} 会被自动注入，子类无需手写任何 setter 模板代码。
 */
public class ServiceInjectionTest {

    /**
     * 验证 baseMapper 被容器自动注入：构建隔离上下文并注册 SqlExecutor/dialect，
     * 刷新后取出的 WidgetService 其 baseMapper 应为 WidgetMapper 代理且非空。
     */
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
