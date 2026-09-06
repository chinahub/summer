package cn.jiebaba.summer.test.context;

import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.annotation.EventListener;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.context.event.ContextClosedEvent;
import cn.jiebaba.summer.core.context.event.ContextRefreshedEvent;
import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 事件机制测试：@EventListener 方法监听、显式事件类型、无参监听器与生命周期事件发布。 */
public class EventListenerTest {

    @Configuration
    static class ListenerConfig {
        final List<String> received = new ArrayList<>();

        @EventListener
        public void onRefreshed(ContextRefreshedEvent event) { received.add("refreshed"); }

        @EventListener(ContextClosedEvent.class)
        public void onClosed() { received.add("closed"); }

        @EventListener
        public void onAny(Object event) { received.add("any:" + event.getClass().getSimpleName()); }
    }

    private static DefaultApplicationContext newContext() {
        DefaultApplicationContext ctx = new DefaultApplicationContext(null, new Environment(), Set.of());
        ctx.registerBeanDefinition("listenerConfig", new BeanDefinition("listenerConfig", ListenerConfig.class));
        return ctx;
    }

    @Test
    public void lifecycleEventsDeliveredToAnnotatedListeners() {
        DefaultApplicationContext ctx = newContext();
        try {
            ctx.refresh();
            ListenerConfig cfg = (ListenerConfig) ctx.getBean("listenerConfig");
            Assertions.assertTrue(cfg.received.contains("refreshed"),
                    "ContextRefreshedEvent 应送达参数推断的监听器");
            Assertions.assertTrue(cfg.received.contains("any:ContextRefreshedEvent"),
                    "ContextRefreshedEvent 应送达 Object 类型监听器");
            ctx.close();
            Assertions.assertTrue(cfg.received.contains("closed"),
                    "ContextClosedEvent 应送达显式类型的无参监听器");
            Assertions.assertTrue(cfg.received.contains("any:ContextClosedEvent"),
                    "ContextClosedEvent 应送达 Object 类型监听器");
        } finally {
            ctx.close();
        }
    }

    @Test
    public void publishEventSupportsCustomEvents() {
        DefaultApplicationContext ctx = newContext();
        try {
            ctx.refresh();
            ListenerConfig cfg = (ListenerConfig) ctx.getBean("listenerConfig");
            int before = cfg.received.size();
            ctx.publishEvent(new ContextClosedEvent(ctx));
            Assertions.assertTrue(cfg.received.size() > before, "自定义发布的事件应送达匹配监听器");
        } finally {
            ctx.close();
        }
    }

    @Test
    public void nonEventOnlyReachesCatchAllListener() {
        DefaultApplicationContext ctx = newContext();
        try {
            ctx.refresh();
            ListenerConfig cfg = (ListenerConfig) ctx.getBean("listenerConfig");
            int before = cfg.received.size();
            ctx.publishEvent("not-an-application-event");
            // Object 类型监听器捕获一切；显式事件类型监听器（refreshed/closed）不受影响
            Assertions.assertEquals(before + 1, cfg.received.size());
            Assertions.assertEquals("any:String", cfg.received.get(before));
        } finally {
            ctx.close();
        }
    }
}
