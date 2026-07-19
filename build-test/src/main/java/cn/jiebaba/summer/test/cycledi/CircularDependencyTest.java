package cn.jiebaba.summer.test.cycledi;

import cn.jiebaba.summer.core.context.BeansException;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/** 验证循环依赖检测：构造器循环在刷新时抛出含路径的异常。 */
public class CircularDependencyTest {

    @Test
    void constructorCycleDetectedWithPath() {
        DefaultApplicationContext ctx = new DefaultApplicationContext(
                null, null, Set.of("cn.jiebaba.summer.test.cycledi"));
        BeansException ex = Assertions.assertThrows(BeansException.class, ctx::refresh);
        String msg = ex.getMessage();
        Assertions.assertTrue(msg.contains("Circular dependency"), "message: " + msg);
        Assertions.assertTrue(msg.contains("cycleA"), "message: " + msg);
        Assertions.assertTrue(msg.contains("->"), "message: " + msg);
    }
}
