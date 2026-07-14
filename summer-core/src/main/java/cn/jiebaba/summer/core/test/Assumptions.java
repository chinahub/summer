package cn.jiebaba.summer.core.test;

/** 假设断言：条件不满足时跳过当前测试而非判定失败。 */
public final class Assumptions {

    private Assumptions() {}

    public static void assumeTrue(boolean condition) {
        assumeTrue(condition, "assumption failed");
    }

    public static void assumeTrue(boolean condition, String message) {
        if (!condition) throw new AssumptionFailure(message == null ? "assumption failed" : message);
    }

    public static void assumeFalse(boolean condition) {
        assumeFalse(condition, "assumption failed");
    }

    public static void assumeFalse(boolean condition, String message) {
        if (condition) throw new AssumptionFailure(message == null ? "assumption failed" : message);
    }

    /** 测试因假设不成立而跳过时抛出的内部信号异常，由 TestRunner 捕获并计为跳过。 */
    public static final class AssumptionFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public AssumptionFailure(String message) {
            super(message);
        }
    }
}
