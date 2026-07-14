package cn.jiebaba.summer.core.test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 极简断言工具，灵感来自 JUnit 5 的 {@code Assertions}。失败时抛出 {@link AssertionError}。 */
public final class Assert {

    private Assert() {}

    public static void fail(String message) {
        throw new AssertionError(message == null ? "fail" : message);
    }

    public static void assertTrue(boolean condition) {
        assertTrue(condition, null);
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message == null ? "expected true" : message);
    }

    public static void assertFalse(boolean condition) {
        assertFalse(condition, null);
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message == null ? "expected false" : message);
    }

    public static void assertNull(Object actual) {
        assertNull(actual, null);
    }

    public static void assertNull(Object actual, String message) {
        if (actual != null) throw new AssertionError(message == null ? "expected null but was " + actual : message);
    }

    public static void assertNotNull(Object actual) {
        assertNotNull(actual, null);
    }

    public static void assertNotNull(Object actual, String message) {
        if (actual == null) throw new AssertionError(message == null ? "expected non-null" : message);
    }

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, null);
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message == null
                    ? "expected <" + expected + "> but was <" + actual + ">"
                    : message + " (expected <" + expected + "> but was <" + actual + ">)");
        }
    }

    public static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertNotEquals(Object unexpected, Object actual) {
        assertNotEquals(unexpected, actual, null);
    }

    public static void assertNotEquals(Object unexpected, Object actual, String message) {
        if (unexpected == null ? actual == null : unexpected.equals(actual)) {
            throw new AssertionError(msg(message, "expected not equal but both were <" + actual + ">"));
        }
    }

    public static void assertNotEquals(long unexpected, long actual) {
        if (unexpected == actual) {
            throw new AssertionError("expected not equal but both were <" + actual + ">");
        }
    }

    public static void assertSame(Object expected, Object actual) {
        assertSame(expected, actual, null);
    }

    public static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(msg(message, "expected same reference"));
    }

    public static void assertNotSame(Object unexpected, Object actual) {
        assertNotSame(unexpected, actual, null);
    }

    public static void assertNotSame(Object unexpected, Object actual, String message) {
        if (unexpected == actual) throw new AssertionError(msg(message, "expected not same reference"));
    }

    public static void assertInstanceOf(Class<?> expected, Object actual) {
        assertInstanceOf(expected, actual, null);
    }

    public static void assertInstanceOf(Class<?> expected, Object actual, String message) {
        if (actual == null || !expected.isInstance(actual)) {
            throw new AssertionError(msg(message, "expected instance of " + expected.getName()
                    + " but was " + (actual == null ? "null" : actual.getClass().getName())));
        }
    }

    public static void assertArrayEquals(Object[] expected, Object[] actual) {
        assertArrayEquals(expected, actual, null);
    }

    public static void assertArrayEquals(Object[] expected, Object[] actual, String message) {
        if (expected == actual) return;
        if (expected == null || actual == null) {
            throw new AssertionError(msg(message, "arrays not equal: one is null"));
        }
        if (expected.length != actual.length) {
            throw new AssertionError(msg(message, "array lengths differ, expected " + expected.length
                    + " but was " + actual.length));
        }
        for (int i = 0; i < expected.length; i++) {
            Object e = expected[i], a = actual[i];
            if (e == null ? a != null : !e.equals(a)) {
                throw new AssertionError(msg(message, "array contents differ at index " + i
                        + ", expected <" + e + "> but was <" + a + ">"));
            }
        }
    }

    public static void assertArrayEquals(int[] expected, int[] actual) {
        if (expected == actual) return;
        if (expected == null || actual == null || expected.length != actual.length) {
            throw new AssertionError("int[] arrays not equal");
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError("int[] differ at index " + i + ": expected " + expected[i] + " but was " + actual[i]);
            }
        }
    }

    public static void assertArrayEquals(long[] expected, long[] actual) {
        if (expected == actual) return;
        if (expected == null || actual == null || expected.length != actual.length) {
            throw new AssertionError("long[] arrays not equal");
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError("long[] differ at index " + i + ": expected " + expected[i] + " but was " + actual[i]);
            }
        }
    }

    public static void assertArrayEquals(double[] expected, double[] actual) {
        if (expected == actual) return;
        if (expected == null || actual == null || expected.length != actual.length) {
            throw new AssertionError("double[] arrays not equal");
        }
        for (int i = 0; i < expected.length; i++) {
            if (Double.doubleToLongBits(expected[i]) != Double.doubleToLongBits(actual[i])) {
                throw new AssertionError("double[] differ at index " + i + ": expected " + expected[i] + " but was " + actual[i]);
            }
        }
    }

    public static void assertArrayEquals(boolean[] expected, boolean[] actual) {
        if (expected == actual) return;
        if (expected == null || actual == null || expected.length != actual.length) {
            throw new AssertionError("boolean[] arrays not equal");
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError("boolean[] differ at index " + i + ": expected " + expected[i] + " but was " + actual[i]);
            }
        }
    }

    public static void assertArrayEquals(char[] expected, char[] actual) {
        if (expected == actual) return;
        if (expected == null || actual == null || expected.length != actual.length) {
            throw new AssertionError("char[] arrays not equal");
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError("char[] differ at index " + i + ": expected " + expected[i] + " but was " + actual[i]);
            }
        }
    }

    public static void assertArrayEquals(byte[] expected, byte[] actual) {
        if (expected == actual) return;
        if (expected == null || actual == null || expected.length != actual.length) {
            throw new AssertionError("byte[] arrays not equal");
        }
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError("byte[] differ at index " + i + ": expected " + expected[i] + " but was " + actual[i]);
            }
        }
    }

    public static void assertIterableEquals(Iterable<?> expected, Iterable<?> actual) {
        assertIterableEquals(expected, actual, null);
    }

    public static void assertIterableEquals(Iterable<?> expected, Iterable<?> actual, String message) {
        if (expected == actual) return;
        if (expected == null || actual == null) {
            throw new AssertionError(msg(message, "one iterable is null"));
        }
        Iterator<?> ie = expected.iterator();
        Iterator<?> ia = actual.iterator();
        int i = 0;
        while (ie.hasNext() && ia.hasNext()) {
            Object e = ie.next();
            Object a = ia.next();
            if (e == null ? a != null : !e.equals(a)) {
                throw new AssertionError(msg(message, "iterable contents differ at index " + i));
            }
            i++;
        }
        if (ie.hasNext() || ia.hasNext()) {
            throw new AssertionError(msg(message, "iterable lengths differ at index " + i));
        }
    }

    public static void assertDoesNotThrow(ThrowingRunnable runnable) {
        assertDoesNotThrow(runnable, null);
    }

    public static void assertDoesNotThrow(ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable t) {
            throw new AssertionError(msg(message, "expected no exception, but "
                    + t.getClass().getName() + " was thrown"), t);
        }
    }

    /**
     * 分组断言：执行全部 runnable，收集所有 {@link AssertionError}，若存在任一失败则合并抛出；
     * 非 AssertionError 的异常会立即向上抛出（视为错误而非断言失败）。
     */
    public static void assertAll(ThrowingRunnable... groups) {
        List<String> failures = new ArrayList<>();
        for (ThrowingRunnable g : groups) {
            try {
                g.run();
            } catch (AssertionError ae) {
                failures.add(ae.getMessage());
            } catch (Throwable t) {
                sneakyThrow(t);
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("assertAll failed (" + failures.size() + " failures): "
                    + String.join("; ", failures));
        }
    }

    /** 断言 runnable 在 {@code timeout} 内完成（会完整执行后才比较耗时）；超时则判定失败。 */
    public static void assertTimeout(Duration timeout, ThrowingRunnable runnable) {
        long start = System.nanoTime();
        try {
            runnable.run();
        } catch (Throwable t) {
            sneakyThrow(t);
        }
        long elapsed = System.nanoTime() - start;
        if (elapsed > timeout.toNanos()) {
            throw new AssertionError("execution exceeded timeout of " + timeout);
        }
    }

    /** 断言 runnable 在 {@code timeout} 内完成；超时则中断并在独立线程中判定失败。 */
    public static void assertTimeoutPreemptively(Duration timeout, ThrowingRunnable runnable) {
        final Throwable[] holder = new Throwable[1];
        Thread worker = new Thread(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                holder[0] = t;
            }
        });
        worker.setDaemon(true);
        worker.start();
        try {
            worker.join(timeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("assertTimeoutPreemptively interrupted", e);
        }
        if (worker.isAlive()) {
            worker.interrupt();
            throw new AssertionError("execution timed out after " + timeout);
        }
        if (holder[0] != null) {
            sneakyThrow(holder[0]);
        }
    }

    /** 运行 runnable 并断言其抛出给定异常类型；返回抛出的实例。 */
    public static <T extends Throwable> T assertThrows(Class<T> expected, ThrowingRunnable runnable) {
        return assertThrows(expected, runnable, null);
    }

    public static <T extends Throwable> T assertThrows(Class<T> expected, ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable t) {
            if (expected.isInstance(t)) return expected.cast(t);
            throw new AssertionError((message == null ? "" : message + ": ")
                    + "expected " + expected.getName() + " but got " + t.getClass().getName(), t);
        }
        throw new AssertionError((message == null ? "" : message + ": ")
                + "expected " + expected.getName() + " to be thrown, but nothing was thrown");
    }

    private static String msg(String message, String detail) {
        return message == null ? detail : message + ": " + detail;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    /** 可抛出任意受检异常的 runnable。 */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
