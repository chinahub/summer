package cn.jiebaba.summer.test.scheduling;

import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.scheduling.ScheduledTaskRegistrar;
import cn.jiebaba.summer.test.scheduling.delay.FixedDelayOverlapTask;
import cn.jiebaba.summer.test.scheduling.fault.GoodTask;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class ScheduledTaskRegistrarTest {

    @Test
    void fixedDelayDoesNotOverlapRuns() throws InterruptedException {
        DefaultApplicationContext ctx = new DefaultApplicationContext(
                null, null, Set.of("cn.jiebaba.summer.test.scheduling.delay"));
        ctx.refresh();
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        registrar.scheduleAll(ctx);
        Thread.sleep(400);
        registrar.shutdown();
        FixedDelayOverlapTask task = ctx.getBean(FixedDelayOverlapTask.class);
        Assertions.assertTrue(task.runs.get() >= 1, "task should have executed at least once");
        Assertions.assertTrue(task.maxConcurrent == 1,
                "fixedDelay must never overlap executions (was " + task.maxConcurrent + ")");
        ctx.close();
    }

    @Test
    void invalidTasksAreSkippedValidTaskStillRuns() throws InterruptedException {
        DefaultApplicationContext ctx = new DefaultApplicationContext(
                null, null, Set.of("cn.jiebaba.summer.test.scheduling.fault"));
        ctx.refresh();
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        int count = registrar.scheduleAll(ctx);
        Assertions.assertEquals(1, count, "only the well-formed task should be registered");
        Thread.sleep(80);
        registrar.shutdown();
        Assertions.assertTrue(ctx.getBean(GoodTask.class).runs.get() >= 1,
                "well-formed task should have run despite sibling failures");
        ctx.close();
    }
}
