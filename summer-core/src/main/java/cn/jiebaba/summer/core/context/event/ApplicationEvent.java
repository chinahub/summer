package cn.jiebaba.summer.core.context.event;

/**
 * 容器事件基类：携带事件源与发生时间戳。所有容器生命周期事件均派生自本类，
 * 通过 {@code ApplicationContext#publishEvent} 发布并由 {@code @EventListener} 方法消费。
 */
public abstract class ApplicationEvent {

    private final Object source;
    private final long timestamp;

    protected ApplicationEvent(Object source) {
        this.source = source;
        this.timestamp = System.currentTimeMillis();
    }

    /** 事件源（发布事件的容器实例）。 */
    public Object getSource() {
        return source;
    }

    /** 事件发生时间戳（毫秒）。 */
    public long getTimestamp() {
        return timestamp;
    }
}
