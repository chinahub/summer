package cn.jiebaba.summer.test.aop.cglib;

import cn.jiebaba.summer.core.aop.SummerProxy;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.BeansException;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.data.transaction.TransactionInterceptor;
import cn.jiebaba.summer.data.transaction.TransactionManager;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CGLIB 风格子类代理 + {@code @Transactional} 专项测试。
 *
 * <p>验证：无接口、非 final 的 {@code @Service} 在带 {@code @Transactional} 方法时，
 * 走手写字节码子类代理（{@link SummerProxy}），事务拦截器在提交/回滚路径上正确织入，
 * 且 final 类无法子类代理时抛 {@link BeansException}。无需真实数据库——用 JDK 动态代理
 * 录制 {@link Connection} 的 JDBC 调用。
 */
public class CglibTransactionalProxyTest {

    /** 记录事务管理器对连接的 JDBC 调用（setAutoCommit/commit/rollback/close）。 */
    static final List<String> jdbcCalls = new ArrayList<>();

    private static Connection recordingConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new RecordingHandler());
    }

    private static DataSource recordingDataSource() {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) return recordingConnection();
                    return defaultReturn(method);
                });
    }

    private static final class RecordingHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("setAutoCommit") || name.equals("commit")
                    || name.equals("rollback") || name.equals("close")) {
                jdbcCalls.add(name);
            }
            return defaultReturn(method);
        }
    }

    private static Object defaultReturn(Method m) {
        Class<?> rt = m.getReturnType();
        if (rt == boolean.class) return false;
        if (rt == int.class) return 0;
        if (rt == long.class) return 0L;
        return null;
    }

    /** 构建一个扫描 cglib 包、并注册了事务拦截器的上下文。 */
    private DefaultApplicationContext fresh() {
        jdbcCalls.clear();
        Environment env = new Environment();
        DefaultApplicationContext ctx = new DefaultApplicationContext(
                getClass().getClassLoader(), env, Set.of("cn.jiebaba.summer.test.aop.cglib"));

        DataSource ds = recordingDataSource();
        TransactionManager tm = new TransactionManager(ds);

        registerSupplier(ctx, "dataSource", DataSource.class, () -> ds);
        registerSupplier(ctx, "transactionManager", TransactionManager.class, () -> tm);
        registerSupplier(ctx, "transactionInterceptor", TransactionInterceptor.class, () -> new TransactionInterceptor(tm));

        ctx.refresh();
        return ctx;
    }

    private static void registerSupplier(DefaultApplicationContext ctx, String name,
                                         Class<?> type, java.util.function.Supplier<Object> supplier) {
        BeanDefinition def = new BeanDefinition(name, type);
        def.setInstanceSupplier(supplier);
        ctx.registerBeanDefinition(name, def);
    }

    @Test
    void noInterfaceTransactionalBeanBecomesSubclassProxy() {
        DefaultApplicationContext ctx = fresh();
        try {
            OrderService svc = ctx.getBean(OrderService.class);
            Assert.assertTrue(svc instanceof SummerProxy,
                    "无接口 @Transactional bean 必须是子类代理（SummerProxy）");
            Assert.assertFalse(Proxy.isProxyClass(svc.getClass()),
                    "无接口 bean 不应走 JDK 动态代理");
            Assert.assertEquals(OrderService.class, svc.getClass().getSuperclass(),
                    "代理类应继承自目标类（CGLIB 风格）");
        } finally {
            ctx.close();
        }
    }

    @Test
    void commitPathRunsTransactionCommit() {
        DefaultApplicationContext ctx = fresh();
        try {
            OrderService svc = ctx.getBean(OrderService.class);
            Assert.assertEquals("committed", svc.commitOk());
            Assert.assertTrue(jdbcCalls.contains("commit"), "成功路径必须提交: " + jdbcCalls);
            Assert.assertFalse(jdbcCalls.contains("rollback"), "成功路径不应回滚: " + jdbcCalls);
            Assert.assertTrue(jdbcCalls.contains("setAutoCommit"), "必须开启事务: " + jdbcCalls);
            Assert.assertTrue(jdbcCalls.contains("close"), "必须关闭连接: " + jdbcCalls);
        } finally {
            ctx.close();
        }
    }

    @Test
    void runtimeExceptionTriggersRollback() {
        DefaultApplicationContext ctx = fresh();
        try {
            OrderService svc = ctx.getBean(OrderService.class);
            RuntimeException ex = Assert.assertThrows(RuntimeException.class, svc::throwsAndRollback);
            Assert.assertEquals("boom", ex.getMessage());
            Assert.assertTrue(jdbcCalls.contains("rollback"), "RuntimeException 必须回滚: " + jdbcCalls);
            Assert.assertFalse(jdbcCalls.contains("commit"), "异常路径不应提交: " + jdbcCalls);
        } finally {
            ctx.close();
        }
    }

    @Test
    void checkedExceptionWithRollbackForTriggersRollback() {
        DefaultApplicationContext ctx = fresh();
        try {
            OrderService svc = ctx.getBean(OrderService.class);
            Assert.assertThrows(Exception.class, svc::throwsCheckedAndRollback);
            Assert.assertTrue(jdbcCalls.contains("rollback"),
                    "rollbackFor=Exception.class 时受检异常必须回滚: " + jdbcCalls);
        } finally {
            ctx.close();
        }
    }

    @Test
    void noRollbackForSkipsRollback() {
        DefaultApplicationContext ctx = fresh();
        try {
            OrderService svc = ctx.getBean(OrderService.class);
            Assert.assertThrows(RuntimeException.class, svc::throwsButNoRollback);
            Assert.assertFalse(jdbcCalls.contains("rollback"),
                    "noRollbackFor=RuntimeException.class 时不应回滚: " + jdbcCalls);
            Assert.assertFalse(jdbcCalls.contains("commit"),
                    "异常路径不应提交: " + jdbcCalls);
        } finally {
            ctx.close();
        }
    }

    @Test
    void finalClassWithTransactionalThrowsBeansException() {
        jdbcCalls.clear();
        Environment env = new Environment();
        DefaultApplicationContext ctx = new DefaultApplicationContext(
                getClass().getClassLoader(), env, Set.of()); // 不扫描
        DataSource ds = recordingDataSource();
        TransactionManager tm = new TransactionManager(ds);
        registerSupplier(ctx, "dataSource", DataSource.class, () -> ds);
        registerSupplier(ctx, "transactionManager", TransactionManager.class, () -> tm);
        registerSupplier(ctx, "transactionInterceptor", TransactionInterceptor.class, () -> new TransactionInterceptor(tm));

        BeanDefinition finalDef = new BeanDefinition("finalOrderService", FinalOrderService.class);
        ctx.registerBeanDefinition("finalOrderService", finalDef);

        Assert.assertThrows(BeansException.class, ctx::refresh,
                "final 类无法子类代理，refresh 必须抛 BeansException");
        ctx.close();
    }
}