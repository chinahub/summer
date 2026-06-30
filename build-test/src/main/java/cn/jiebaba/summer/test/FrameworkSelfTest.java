package cn.jiebaba.summer.test;

import cn.jiebaba.summer.core.test.AfterEach;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.BeforeEach;
import cn.jiebaba.summer.core.test.Test;

/** Self-test for the tiny test framework. All cases here must pass. */
public class FrameworkSelfTest {

    private int beforeCount;
    private int afterCount;
    private static int afterRuns;

    @BeforeEach
    void setUp() {
        beforeCount++;
    }

    @AfterEach
    void tearDown() {
        afterCount++;
        afterRuns++;
    }

    @Test
    void assertEqualsPasses() {
        Assert.assertEquals("a", "a");
        Assert.assertEquals(42L, 42L);
    }

    @Test
    void assertThrowsCatchesAndReturnsCause() {
        IllegalStateException ex = Assert.assertThrows(IllegalStateException.class,
                () -> { throw new IllegalStateException("boom"); });
        Assert.assertEquals("boom", ex.getMessage());
    }

    @Test
    void assertThrowsFailsWhenNothingThrown() {
        Assert.assertThrows(AssertionError.class,
                () -> Assert.assertThrows(IllegalStateException.class, () -> {}, "none"));
    }

    @Test
    void assertThrowsFailsOnWrongType() {
        Assert.assertThrows(AssertionError.class,
                () -> Assert.assertThrows(IllegalStateException.class,
                        () -> { throw new IllegalArgumentException("nope"); }));
    }

    @Test(expected = ArithmeticException.class)
    void expectedExceptionAnnotation() {
        int ignored = 1 / 0;
    }

    @Test
    void beforeEachRanOnceBeforeTest() {
        Assert.assertEquals(1, beforeCount);
        Assert.assertEquals(0, afterCount);
    }

    // Named to sort last so prior tests' @AfterEach have run.
    @Test
    void zzAfterEachHasRunForPriorTest() {
        Assert.assertTrue(afterRuns >= 1, "at least one @AfterEach should have run");
    }

    @Test
    void assertNullAndNotNull() {
        Assert.assertNull(null);
        Assert.assertNotNull("x");
    }
}