package cn.jiebaba.summer.test.framework;

import cn.jiebaba.summer.core.test.AfterAll;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Assumptions;
import cn.jiebaba.summer.core.test.BeforeAll;
import cn.jiebaba.summer.core.test.BeforeEach;
import cn.jiebaba.summer.core.test.Disabled;
import cn.jiebaba.summer.core.test.DisplayName;
import cn.jiebaba.summer.core.test.ParameterizedTest;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.core.test.ValueSource;

import java.time.Duration;
import java.util.List;

/**
 * 验证内置测试框架增强后的能力：类级生命周期、显示名、禁用、参数化测试、
 * 假设跳过，以及新增的分组/超时/数组/可迭代断言。
 */
public class TestFrameworkTest {

    private static int beforeAllCount = 0;
    private int counter = 0;

    @BeforeAll
    static void setUpAll() {
        beforeAllCount++;
    }

    @AfterAll
    static void tearDownAll() {
    }

    @BeforeEach
    void setUp() {
        counter = 10;
    }

    @Test
    @DisplayName("BeforeEach 已执行")
    void beforeEachRan() {
        Assert.assertEquals(10, counter);
    }

    @Test
    void assertNotEqualsAndSame() {
        Assert.assertNotEquals("a", "b");
        String s = "x";
        Assert.assertSame(s, s);
        Assert.assertNotSame(new Object(), new Object());
    }

    @Test
    void assertArrayAndIterable() {
        Assert.assertArrayEquals(new int[]{1, 2, 3}, new int[]{1, 2, 3});
        Assert.assertIterableEquals(List.of(1, 2), List.of(1, 2));
    }

    @Test
    void assertDoesNotThrowAndAll() {
        Assert.assertDoesNotThrow(() -> {});
        Assert.assertAll(
                () -> Assert.assertEquals(1, 1),
                () -> Assert.assertTrue(true)
        );
    }

    @Test
    void assertInstanceOfWorks() {
        Assert.assertInstanceOf(String.class, "hello");
    }

    @Test
    void assertThrowsReturnsInstance() {
        NumberFormatException ex = Assert.assertThrows(NumberFormatException.class,
                () -> Integer.parseInt("x"));
        Assert.assertNotNull(ex);
    }

    @Test
    void assertTimeoutPasses() {
        Assert.assertTimeout(Duration.ofMillis(500), () -> {});
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void squaredIsPositive(int n) {
        Assert.assertTrue(n * n > 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "b", "c"})
    void stringNotBlank(String s) {
        Assert.assertFalse(s.isBlank());
    }

    @Test
    void assumptionSkipsWhenFalse() {
        Assumptions.assumeTrue(false, "intentionally skipped to verify skip");
        Assert.fail("should not reach here when assumption fails");
    }

    @Disabled("演示禁用：始终跳过")
    @Test
    void disabledTestNeverRuns() {
        Assert.fail("disabled test ran");
    }

    @Test
    void beforeAllRanOnce() {
        Assert.assertTrue(beforeAllCount >= 1, "BeforeAll should have run at least once");
    }
}
