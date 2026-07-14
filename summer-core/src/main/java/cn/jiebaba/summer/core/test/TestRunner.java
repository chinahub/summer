package cn.jiebaba.summer.core.test;

import cn.jiebaba.summer.core.scanner.ClassPathScanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 类似 JUnit 的微型测试运行器，借鉴 JUnit 5 的关键能力：
 * {@link BeforeAll}/{@link AfterAll} 类级生命周期、{@link DisplayName} 可读名称、
 * {@link Disabled} 跳过、{@link ParameterizedTest}+{@link ValueSource} 参数化测试、
 * {@link Assumptions} 条件跳过。通过 {@link ClassPathScanner} 发现给定基础包下的类，
 * 全部通过则退出码 0，否则为 1。
 *
 * <p>用法：{@code java -cp ... cn.jiebaba.summer.core.test.TestRunner [package ...]}
 */
public final class TestRunner {

    private TestRunner() {}

    /**
     * 测试运行入口：扫描并执行 @Test/@ParameterizedTest 方法，汇总通过/失败/错误/跳过结果。
     */
    public static void main(String[] args) throws Exception {
        Set<String> basePackages = new LinkedHashSet<>();
        if (args != null) {
            for (String a : args) {
                if (a != null && !a.isBlank()) basePackages.add(a.trim());
            }
        }
        if (basePackages.isEmpty()) basePackages.add("cn.jiebaba.summer");

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = TestRunner.class.getClassLoader();

        Set<Class<?>> classes = ClassPathScanner.scan(basePackages, cl);

        int passed = 0, failed = 0, errored = 0, skipped = 0;
        List<String> details = new ArrayList<>();

        for (Class<?> cls : classes) {
            if (cls.isInterface() || cls.isAnnotation() || Modifier.isAbstract(cls.getModifiers())) continue;
            List<Method> tests = collectTests(cls);
            if (tests.isEmpty()) continue;

            if (cls.isAnnotationPresent(Disabled.class)) {
                String reason = cls.getAnnotation(Disabled.class).value();
                for (Method m : tests) {
                    skipped++;
                    details.add("SKIP   " + displayName(cls, m) + " :: @Disabled on class"
                            + (reason.isEmpty() ? "" : ": " + reason));
                }
                continue;
            }

            String setupError = null;
            try {
                invokeStaticLifecycle(cls, BeforeAll.class);
            } catch (Throwable t) {
                setupError = "@BeforeAll -> " + rootMessage(t);
            }

            for (Method test : tests) {
                List<Object[]> invs;
                try {
                    invs = invocations(cls, test);
                } catch (Throwable t) {
                    errored++;
                    details.add("ERROR  " + displayName(cls, test) + " :: " + rootMessage(t));
                    continue;
                }

                if (test.isAnnotationPresent(Disabled.class)) {
                    String reason = test.getAnnotation(Disabled.class).value();
                    skipped += invs.size();
                    details.add("SKIP   " + displayName(cls, test) + " :: @Disabled"
                            + (reason.isEmpty() ? "" : ": " + reason));
                    continue;
                }
                if (setupError != null) {
                    errored += invs.size();
                    details.add("ERROR  " + displayName(cls, test) + " :: " + setupError);
                    continue;
                }

                for (Object[] invocationArgs : invs) {
                    Outcome o = runOne(cls, test, invocationArgs);
                    String label = displayName(cls, test);
                    if (invs.size() > 1) label += "[" + argLabel(invocationArgs) + "]";
                    switch (o.kind) {
                        case PASS -> passed++;
                        case FAIL -> { failed++; details.add("FAIL   " + label + " :: " + o.message); }
                        case ERROR -> { errored++; details.add("ERROR  " + label + " :: " + o.message); }
                        case SKIP -> { skipped++; details.add("SKIP   " + label + " :: " + o.message); }
                    }
                }
            }

            try {
                invokeStaticLifecycle(cls, AfterAll.class);
            } catch (Throwable t) {
                details.add("ERROR  " + cls.getName() + " @AfterAll :: " + rootMessage(t));
            }
        }

        System.out.println();
        System.out.println("Results: " + passed + " passed, " + failed + " failed, "
                + errored + " errored, " + skipped + " skipped");
        if (!details.isEmpty()) {
            System.out.println("----");
            for (String d : details) System.out.println(d);
        }

        if (failed + errored > 0) System.exit(1);
    }

    private enum Kind { PASS, FAIL, ERROR, SKIP }

    private record Outcome(Kind kind, String message) {
        static Outcome pass() { return new Outcome(Kind.PASS, null); }
        static Outcome fail(String m) { return new Outcome(Kind.FAIL, m); }
        static Outcome error(String m) { return new Outcome(Kind.ERROR, m); }
        static Outcome skip(String m) { return new Outcome(Kind.SKIP, m); }
    }

    /** 收集类中标注 @Test（无参）或 @ParameterizedTest（单参）的非静态 void 方法。 */
    private static List<Method> collectTests(Class<?> cls) {
        List<Method> tests = new ArrayList<>();
        for (Method m : cls.getDeclaredMethods()) {
            boolean isTest = m.isAnnotationPresent(Test.class);
            boolean isParam = m.isAnnotationPresent(ParameterizedTest.class);
            if (!isTest && !isParam) continue;
            if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class) continue;
            if (isTest && m.getParameterCount() != 0) continue;
            if (isParam && m.getParameterCount() != 1) continue;
            tests.add(m);
        }
        tests.sort((x, y) -> x.getName().compareTo(y.getName()));
        return tests;
    }

    /** 计算测试方法的所有调用实参组：@Test 为单次空参，@ParameterizedTest 按 @ValueSource 展开。 */
    private static List<Object[]> invocations(Class<?> cls, Method m) {
        if (m.isAnnotationPresent(ParameterizedTest.class)) {
            ValueSource vs = m.getAnnotation(ValueSource.class);
            if (vs == null) {
                throw new IllegalStateException("@ParameterizedTest " + cls.getName() + "." + m.getName() + " 缺少 @ValueSource");
            }
            Class<?> paramType = m.getParameterTypes()[0];
            List<Object[]> result = new ArrayList<>();
            for (Object arg : valueSourceArgs(vs, paramType)) result.add(new Object[]{arg});
            if (result.isEmpty()) {
                throw new IllegalStateException("@ParameterizedTest " + cls.getName() + "." + m.getName() + " 的 @ValueSource 未提供实参");
            }
            return result;
        }
        return List.<Object[]>of(new Object[0]);
    }

    /** 按方法参数类型从 @ValueSource 选取对应字面量数组并装箱为列表。 */
    private static List<Object> valueSourceArgs(ValueSource vs, Class<?> type) {
        List<Object> args = new ArrayList<>();
        if (type == int.class || type == Integer.class) {
            for (int v : vs.ints()) args.add(v);
        } else if (type == long.class || type == Long.class) {
            for (long v : vs.longs()) args.add(v);
        } else if (type == double.class || type == Double.class) {
            for (double v : vs.doubles()) args.add(v);
        } else if (type == boolean.class || type == Boolean.class) {
            for (boolean v : vs.booleans()) args.add(v);
        } else if (type == char.class || type == Character.class) {
            for (char v : vs.chars()) args.add(v);
        } else if (type == String.class) {
            for (String v : vs.strings()) args.add(v);
        } else if (type == Class.class) {
            for (Class<?> v : vs.classes()) args.add(v);
        } else {
            throw new IllegalStateException("@ValueSource 不支持参数类型: " + type);
        }
        return args;
    }

    /** 生成测试在报告中的显示名：优先使用方法上的 @DisplayName，否则用类名.方法名。 */
    private static String displayName(Class<?> cls, Method m) {
        DisplayName dn = m.getAnnotation(DisplayName.class);
        String name = (dn != null && !dn.value().isEmpty()) ? dn.value() : m.getName();
        return cls.getName() + "." + name;
    }

    private static String argLabel(Object[] args) {
        if (args.length == 0) return "";
        Object a = args[0];
        if (a instanceof String s) return s;
        if (a instanceof Class<?> c) return c.getSimpleName();
        return String.valueOf(a);
    }

    /**
     * 执行单次测试调用：实例化、运行 @BeforeEach/@AfterEach 回退，捕获通过/失败/错误/跳过。
     */
    private static Outcome runOne(Class<?> cls, Method test, Object[] args) {
        Object instance;
        try {
            instance = newInstance(cls);
        } catch (Throwable t) {
            return Outcome.error("<init> -> " + rootMessage(t));
        }

        try {
            invokeLifecycle(cls, instance, BeforeEach.class);
        } catch (Assumptions.AssumptionFailure af) {
            return Outcome.skip(af.getMessage());
        } catch (Throwable t) {
            return Outcome.error("@BeforeEach -> " + rootMessage(t));
        }

        Throwable thrown = null;
        boolean assumed = false;
        String assumeMsg = null;
        try {
            test.setAccessible(true);
            test.invoke(instance, args);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof Assumptions.AssumptionFailure af) {
                assumed = true;
                assumeMsg = af.getMessage();
            } else {
                thrown = cause;
            }
        } catch (Throwable t) {
            thrown = t;
        }

        try {
            invokeLifecycle(cls, instance, AfterEach.class);
        } catch (Assumptions.AssumptionFailure af) {
            return Outcome.skip(af.getMessage());
        } catch (Throwable t) {
            return Outcome.error("@AfterEach -> " + rootMessage(t));
        }

        if (assumed) return Outcome.skip(assumeMsg);

        Test ann = test.getAnnotation(Test.class);
        boolean expectThrow = ann != null && ann.expected() != Throwable.class;

        if (thrown == null) {
            if (expectThrow) return Outcome.fail("expected " + ann.expected().getName() + " but nothing was thrown");
            return Outcome.pass();
        }
        if (thrown instanceof AssertionError ae && !expectThrow) {
            return Outcome.fail(ae.getMessage());
        }
        if (expectThrow) {
            if (ann.expected().isInstance(thrown)) return Outcome.pass();
            return Outcome.fail("expected " + ann.expected().getName() + " but got " + thrown.getClass().getName());
        }
        return Outcome.error(rootMessage(thrown));
    }

    private static Object newInstance(Class<?> cls) throws Exception {
        try {
            java.lang.reflect.Constructor<?> c = cls.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("test class has no no-arg constructor: " + cls.getName(), e);
        }
    }

    /** 执行实例级生命周期方法（@BeforeEach/@AfterEach），无参、void 即可，不要求 static。 */
    private static void invokeLifecycle(Class<?> cls, Object instance,
                                        Class<? extends Annotation> marker) throws Throwable {
        for (Method m : cls.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(marker)) continue;
            if (m.getParameterCount() != 0 || m.getReturnType() != void.class) continue;
            m.setAccessible(true);
            try {
                m.invoke(instance);
            } catch (InvocationTargetException ite) {
                throw ite.getCause();
            }
        }
    }

    /** 执行类级静态生命周期方法（@BeforeAll/@AfterAll），必须为 static、无参、void。 */
    private static void invokeStaticLifecycle(Class<?> cls, Class<? extends Annotation> marker) throws Throwable {
        for (Method m : cls.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(marker)) continue;
            if (m.getParameterCount() != 0 || m.getReturnType() != void.class) continue;
            if (!Modifier.isStatic(m.getModifiers())) {
                throw new IllegalStateException(marker.getSimpleName() + " 方法必须为 static: " + cls.getName() + "." + m.getName());
            }
            m.setAccessible(true);
            try {
                m.invoke(null);
            } catch (InvocationTargetException ite) {
                throw ite.getCause();
            }
        }
    }

    private static String rootMessage(Throwable t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        while (cur != null) {
            if (sb.length() > 0) sb.append(" -> ");
            sb.append(cur.getClass().getSimpleName()).append(": ").append(cur.getMessage());
            cur = cur.getCause();
        }
        return sb.toString();
    }
}
