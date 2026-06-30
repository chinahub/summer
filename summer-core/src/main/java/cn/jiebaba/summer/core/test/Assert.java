package cn.jiebaba.summer.core.test;

/** Minimal assertion helpers. Failures throw {@link AssertionError}. */
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

    /** Runs the runnable and asserts it throws the given exception type; returns the thrown instance. */
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

    /** A runnable that may throw any checked exception. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }
}