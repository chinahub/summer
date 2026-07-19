package cn.jiebaba.summer.core.test;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * summer 容器的 JUnit 5 扩展（参照 SpringExtension / QuarkusTestExtension 的整合形态）：
 * 随 {@link SummerTest} 注册，在真实 Jupiter 引擎上为每个测试类启动一个
 * {@link DefaultApplicationContext}，并支持两类注入——
 * 测试实例的 {@code @Autowired} 字段注入、测试方法的 bean 类型参数注入。
 *
 * <p>生命周期：{@code beforeAll} 创建并刷新容器存入 {@link ExtensionContext.Store}；
 * {@code afterAll} 关闭容器。容器按测试类共享。
 */
public class SummerExtension implements BeforeAllCallback, AfterAllCallback,
        TestInstancePostProcessor, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(SummerExtension.class);
    private static final String KEY = "applicationContext";

    /** 全部用例执行前：按 @SummerTest 指定的基础包（缺省为测试类所在包）创建并刷新容器。 */
    @Override
    public void beforeAll(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        Set<String> basePackages = new LinkedHashSet<>();
        SummerTest ann = testClass.getAnnotation(SummerTest.class);
        if (ann != null) {
            basePackages.addAll(Arrays.asList(ann.value()));
        }
        if (basePackages.isEmpty()) {
            basePackages.add(testClass.getPackageName());
        }
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, null, basePackages);
        ctx.refresh();
        context.getStore(NAMESPACE).put(KEY, ctx);
    }

    /** 测试实例创建后：为标注 @Autowired 的非静态字段按类型注入容器 bean。 */
    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
        DefaultApplicationContext ctx = applicationContext(context);
        for (Field field : allFields(testInstance.getClass())) {
            if (!field.isAnnotationPresent(Autowired.class) || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                field.set(testInstance, ctx.getBean(field.getType()));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("注入测试字段失败: " + field, e);
            }
        }
    }

    /** 判断测试方法参数能否解析：参数类型在容器中存在对应 bean 即支持。 */
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return applicationContext(extensionContext).getBeanNamesForType(type).length > 0;
    }

    /** 解析测试方法参数：按参数类型从容器取 bean 注入。 */
    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return applicationContext(extensionContext).getBean(parameterContext.getParameter().getType());
    }

    /** 全部用例执行后：关闭容器并清理 Store。 */
    @Override
    public void afterAll(ExtensionContext context) {
        DefaultApplicationContext ctx = context.getStore(NAMESPACE).remove(KEY, DefaultApplicationContext.class);
        if (ctx != null) {
            ctx.close();
        }
    }

    /** 从 Store 取当前测试类的容器；未初始化（如未标注 @SummerTest）时抛出明确错误。 */
    private static DefaultApplicationContext applicationContext(ExtensionContext context) {
        DefaultApplicationContext ctx = context.getStore(NAMESPACE).get(KEY, DefaultApplicationContext.class);
        if (ctx == null) {
            throw new IllegalStateException("summer 容器未初始化，请在测试类上标注 @SummerTest");
        }
        return ctx;
    }

    /** 收集类层次中的全部声明字段（含父类）。 */
    private static Set<Field> allFields(Class<?> type) {
        Set<Field> fields = new LinkedHashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }
}
