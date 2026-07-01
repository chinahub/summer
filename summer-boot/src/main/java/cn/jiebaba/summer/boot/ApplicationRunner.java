package cn.jiebaba.summer.boot;

/**
 * Hook executed once the application context is fully started and the web server is listening.
 * <p>Beans implementing this interface are discovered by {@link SummerApplication#run} and have
 * their {@link #run} method invoked after all singletons are initialized and the web server has
 * started. Multiple runners are ordered by {@link cn.jiebaba.summer.core.annotation.Order @Order}
 * (ascending; unset orders run last). Typical uses include cache warm-up, loading dictionary data
 * into memory, startup banner printing, and verifying that external services are reachable.
 *
 * <p>This is the summer equivalent of Spring Boot's {@code ApplicationRunner}.
 */
@FunctionalInterface
public interface ApplicationRunner {

    /**
     * Run the startup logic.
     *
     * @param args the application arguments, never {@code null}
     * @throws Exception if startup logic fails; the application start-up will be aborted
     */
    void run(ApplicationArguments args) throws Exception;
}