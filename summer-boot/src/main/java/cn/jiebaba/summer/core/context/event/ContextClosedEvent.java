package cn.jiebaba.summer.core.context.event;

import cn.jiebaba.summer.core.context.ApplicationContext;

/**
 * 容器关闭事件：在销毁各 Bean 之前发布，供监听器在资源释放前完成收尾（flush/告警等）。
 */
public class ContextClosedEvent extends ApplicationEvent {

    public ContextClosedEvent(ApplicationContext source) {
        super(source);
    }
}
