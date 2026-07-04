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
 * 验证 {@link SummerApplication#invokeRunners}：发现 {@link ApplicationRunner} Bean、
 * 基于 {@link Order @Order} 的排序、参数解析以及失败传播。
 *
 * <p>Bean 以编程方式注册（非组件扫描），使测试保持隔离、不依赖 data/web 层。
 * {@link DefaultApplicationContext#registerBean} 保留真实 Bean 类型供 {@code getType} 可见，
 * 因此 {@code @Order} 从目标类读取。
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
        // 有序的先执行（升序），无序的随后（保持注册顺序）
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
