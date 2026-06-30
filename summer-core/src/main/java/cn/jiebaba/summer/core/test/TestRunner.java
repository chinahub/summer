package cn.jiebaba.summer.core.test;

import cn.jiebaba.summer.core.scanner.ClassPathScanner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A tiny JUnit-like test runner. Discovers classes under the given base
 * packages via {@link ClassPathScanner}, then for each {@code @Test} method
 * creates a fresh instance, runs surrounding {@code @BeforeEach}/{@code @AfterEach},
 * and exits with code 0 if all pass, 1 otherwise.
 *
 * Usage: {@code java -cp ... cn.jiebaba.summer.core.test.TestRunner [package ...]}
 */
public final class TestRunner {

    private TestRunner() {}

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

        int passed = 0, failed = 0, errored = 0;
        List<String> details = new ArrayList<>();

        for (Class<?> cls : classes) {
            if (cls.isInterface() || cls.isAnnotation() || Modifier.isAbstract(cls.getModifiers())) continue;
            List<Method> tests = collectTests(cls);
            if (tests.isEmpty()) continue;

            for (Method test : tests) {
                Outcome o = runOne(cls, test);
                switch (o.kind) {
                    case PASS -> passed++;
                    case FAIL -> { failed++; details.add("FAIL   " + cls.getName() + "." + test.getName() + " :: " + o.message); }
                    case ERROR -> { errored++; details.add("ERROR  " + cls.getName() + "." + test.getName() + " :: " + o.message); }
                }
            }
        }

        System.out.println();
        System.out.println("Results: " + passed + " passed, " + failed + " failed, " + errored + " errored");
        if (!details.isEmpty()) {
            System.out.println("----");
            for (String d : details) System.out.println(d);
        }

        if (failed + errored > 0) System.exit(1);
    }

    private enum Kind { PASS, FAIL, ERROR }

    private record Outcome(Kind kind, String message) {
        static Outcome pass() { return new Outcome(Kind.PASS, null); }
        static Outcome fail(String m) { return new Outcome(Kind.FAIL, m); }
        static Outcome error(String m) { return new Outcome(Kind.ERROR, m); }
    }

    private static List<Method> collectTests(Class<?> cls) {
        List<Method> tests = new ArrayList<>();
        for (Method m : cls.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Test.class)) continue;
            if (m.getParameterCount() != 0 || m.getReturnType() != void.class
                    || Modifier.isStatic(m.getModifiers())) continue;
            tests.add(m);
        }
        tests.sort((x, y) -> x.getName().compareTo(y.getName()));
        return tests;
    }

    private static Outcome runOne(Class<?> cls, Method test) {
        Object instance;
        try {
            instance = newInstance(cls);
        } catch (Throwable t) {
            return Outcome.error("<init> -> " + rootMessage(t));
        }

        Test ann = test.getAnnotation(Test.class);
        boolean expectThrow = ann.expected() != Throwable.class;

        try {
            invokeLifecycle(cls, instance, BeforeEach.class);
        } catch (Throwable t) {
            return Outcome.error("@BeforeEach -> " + rootMessage(t));
        }

        Throwable thrown = null;
        try {
            test.setAccessible(true);
            test.invoke(instance);
        } catch (InvocationTargetException ite) {
            thrown = ite.getCause();
        } catch (Throwable t) {
            thrown = t;
        }

        try {
            invokeLifecycle(cls, instance, AfterEach.class);
        } catch (Throwable t) {
            return Outcome.error("@AfterEach -> " + rootMessage(t));
        }

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

    private static void invokeLifecycle(Class<?> cls, Object instance,
                                        Class<? extends java.lang.annotation.Annotation> marker) throws Throwable {
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