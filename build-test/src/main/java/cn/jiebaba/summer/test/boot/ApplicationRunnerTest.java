package cn.jiebaba.summer.test.boot;

import cn.jiebaba.summer.boot.ApplicationArguments;
import cn.jiebaba.summer.boot.ApplicationRunner;
import cn.jiebaba.summer.boot.DefaultApplicationArguments;
import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.core.annotation.Order;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.BeforeEach;
import cn.jiebaba.summer.core.test.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Verifies {@link SummerApplication#invokeRunners}: discovery of {@link ApplicationRunner} beans,
 * {@link Order @Order}-based sequencing, argument parsing, and failure propagation.
 *
 * <p>Beans are registered programmatically (not via component scanning) so the test stays isolated
 * and does not depend on the data/web layers. {@link DefaultApplicationContext#registerBean} keeps
 * the real bean class visible to {@code getType}, so {@code @Order} is read from the target class.
 */
public class ApplicationRunnerTest {

    private static final List<String> executed = new ArrayList<>();
    private static ApplicationArguments capturedArgs;

    @Order(1)
    static class FirstRunner implements ApplicationRunner {
        public void run(ApplicationArguments args) { executed.add("first"); }
    }

    @Order(3)
    static class ThirdRunner implements ApplicationRunner {
        public void run(ApplicationArguments args) { executed.add("third"); }
    }

    static class UnorderedRunner implements ApplicationRunner {
        public void run(ApplicationArguments args) { executed.add("unordered"); }
    }

    static class ArgsRunner implements ApplicationRunner {
        public void run(ApplicationArguments args) { capturedArgs = args; }
    }

    static class FailingRunner implements ApplicationRunner {
        public void run(ApplicationArguments args) { throw new RuntimeException("boom"); }
    }

    private DefaultApplicationContext context;

    @BeforeEach
    public void setUp() {
        executed.clear();
        capturedArgs = null;
        context = new DefaultApplicationContext(null, null, Set.of());
    }

    @Test
    public void runnersExecuteInOrder() {
        context.registerBean("third", new ThirdRunner());
        context.registerBean("first", new FirstRunner());
        context.registerBean("unordered", new UnorderedRunner());
        SummerApplication.invokeRunners(context, new String[0]);
        // ordered first (ascending), then unordered (registration order preserved)
        Assert.assertEquals(List.of("first", "third", "unordered"), executed);
    }

    @Test
    public void noRunnersIsNoop() {
        SummerApplication.invokeRunners(context, new String[0]);
        Assert.assertTrue(executed.isEmpty());
    }

    @Test
    public void argsAreParsedAndPassed() {
        context.registerBean("args", new ArgsRunner());
        SummerApplication.invokeRunners(context,
                new String[]{"--name=summer", "--debug", "positional"});
        Assert.assertNotNull(capturedArgs);
        Assert.assertTrue(capturedArgs.containsOption("name"));
        Assert.assertTrue(capturedArgs.containsOption("debug"));
        Assert.assertEquals("summer", capturedArgs.getOptionValues("name").get(0));
        Assert.assertEquals(List.of("positional"), capturedArgs.getNonOptionArgs());
    }

    @Test
    public void failingRunnerThrowsIllegalState() {
        context.registerBean("fail", new FailingRunner());
        Assert.assertThrows(IllegalStateException.class,
                () -> SummerApplication.invokeRunners(context, new String[0]));
    }

    @Test
    public void defaultArgsParsesFlagsAndRepeats() {
        DefaultApplicationArguments args =
                new DefaultApplicationArguments("--flag", "--k=1", "--k=2", "leftover");
        Assert.assertTrue(args.containsOption("flag"));
        Assert.assertTrue(args.getOptionValues("flag").isEmpty());
        Assert.assertEquals(List.of("1", "2"), args.getOptionValues("k"));
        Assert.assertEquals(List.of("leftover"), args.getNonOptionArgs());
        Assert.assertEquals(4, args.getSourceArgs().length);
    }
}