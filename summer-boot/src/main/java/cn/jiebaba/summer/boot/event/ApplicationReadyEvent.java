package cn.jiebaba.summer.boot.event;

import cn.jiebaba.summer.core.context.event.ApplicationEvent;

/**
 * 应用就绪事件：Web 服务器开始监听、定时任务注册完成且全部 {@link cn.jiebaba.summer.boot.ApplicationRunner}
 * 执行成功后发布。适合做预热、缓存加载、健康上报等启动完成后的动作。
 */
public class ApplicationReadyEvent extends ApplicationEvent {

    public ApplicationReadyEvent(Object source) {
        super(source);
    }
}
