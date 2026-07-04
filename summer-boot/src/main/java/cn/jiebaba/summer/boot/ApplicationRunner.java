package cn.jiebaba.summer.boot;

/**
 * 在应用上下文完全启动且 Web 服务器开始监听后执行的钩子。
 * <p>实现该接口的 Bean 会被 {@link SummerApplication#run} 发现，并在所有单例初始化完成、
 * Web 服务器启动之后调用其 {@link #run} 方法。多个 runner 按
 * {@link cn.jiebaba.summer.core.annotation.Order @Order} 升序排序（未设置顺序的排在最后，
 * 并按发现顺序执行）。典型用途包括缓存预热、将字典数据载入内存、打印启动横幅以及
 * 校验外部服务是否可达。
 *
 * <p>这是 summer 对 Spring Boot {@code ApplicationRunner} 的等价实现。
 */
@FunctionalInterface
public interface ApplicationRunner {

    /**
     * 执行启动逻辑。
     *
     * @param args 应用参数，不为 {@code null}
     * @throws Exception 若启动逻辑失败则抛出，应用启动将被中止
     */
    void run(ApplicationArguments args) throws Exception;
}
