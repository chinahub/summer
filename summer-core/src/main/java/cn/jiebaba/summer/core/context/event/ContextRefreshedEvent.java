package cn.jiebaba.summer.core.context.event;

import cn.jiebaba.summer.core.context.ApplicationContext;

/**
 * 容器刷新完成事件：所有 Bean 定义注册、实例化与 AOP 织入结束后发布。
 */
public class ContextRefreshedEvent extends ApplicationEvent {

    public ContextRefreshedEvent(ApplicationContext source) {
        super(source);
    }
}
