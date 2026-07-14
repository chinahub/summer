package cn.jiebaba.summer.test.protodi;

import cn.jiebaba.summer.core.context.BeansException;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

import java.util.Set;

/** 验证原型循环依赖检测：刷新成功，但获取原型 Bean 时抛出含路径的异常。 */
public class PrototypeCycleTest {

    @Test
    void prototypeCycleDetectedOnGet() {
        DefaultApplicationContext ctx = new DefaultApplicationContext(
                null, null, Set.of("cn.jiebaba.summer.test.protodi"));
        ctx.refresh();
        BeansException ex = Assert.assertThrows(BeansException.class, () -> ctx.getBean(ProtoA.class));
        String msg = ex.getMessage();
        Assert.assertTrue(msg.contains("prototype"), "message: " + msg);
        Assert.assertTrue(msg.contains("protoA"), "message: " + msg);
        Assert.assertTrue(msg.contains("->"), "message: " + msg);
    }
}
