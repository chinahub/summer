package cn.jiebaba.summer.boot.event;

import cn.jiebaba.summer.core.context.event.ApplicationEvent;

/**
 * 启动失败事件：容器已成功刷新后启动流程（如 Web 启动、Runner 执行）抛出异常时发布。
 * 容器刷新之前发生的失败不会发布（此时事件监听器尚未就绪）。
 */
public class ApplicationFailedEvent extends ApplicationEvent {

    private final String stage;
    private final Throwable failure;

    public ApplicationFailedEvent(Object source, String stage, Throwable failure) {
        super(source);
        this.stage = stage;
        this.failure = failure;
    }

    /** 失败发生的阶段：container/web/runners。 */
    public String getStage() {
        return stage;
    }

    /** 导致失败的异常。 */
    public Throwable getFailure() {
        return failure;
    }
}
