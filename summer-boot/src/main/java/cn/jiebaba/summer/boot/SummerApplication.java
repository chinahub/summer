package cn.jiebaba.summer.boot;

import cn.jiebaba.summer.boot.annotation.SummerBootApplication;
import cn.jiebaba.summer.core.annotation.ComponentScan;
import cn.jiebaba.summer.core.annotation.Order;
import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.util.SummerUtil;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.logging.LoggingInitializer;
import cn.jiebaba.summer.core.scheduling.ScheduledTaskRegistrar;
import cn.jiebaba.summer.core.scanner.AnnotationUtils;
import cn.jiebaba.summer.web.convert.JsonMessageConverter;
import cn.jiebaba.summer.web.convert.MessageConverter;
import cn.jiebaba.summer.web.routing.Router;
import cn.jiebaba.summer.web.server.SummerWebServer;
import cn.jiebaba.summer.web.server.WebServerProperties;
import cn.jiebaba.summer.web.support.ExceptionHandlerRegistry;
import cn.jiebaba.summer.boot.data.DataAutoConfiguration;
import cn.jiebaba.summer.boot.security.SecurityAutoConfiguration;
import cn.jiebaba.summer.boot.data.MapperRegistrar;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.web.support.WebRouteRegistrar;
import cn.jiebaba.summer.web.bind.HandlerMethodAccessChecker;
import cn.jiebaba.summer.web.filter.Filter;
import java.util.List;
import cn.jiebaba.summer.security.web.SecurityFilterChain;
import cn.jiebaba.summer.web.websocket.WebSocketRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public final class SummerApplication {

    private static final Logger LOG = Logger.getLogger(SummerApplication.class.getName());

    private final ApplicationContext context;
    private final SummerWebServer webServer;
    private final ScheduledTaskRegistrar scheduler;

    private SummerApplication(ApplicationContext context, SummerWebServer webServer, ScheduledTaskRegistrar scheduler) {
        this.context = context;
        this.webServer = webServer;
        this.scheduler = scheduler;
    }

    public ApplicationContext context() { return context; }
    public SummerWebServer webServer() { return webServer; }

    /** Run with an explicit primary source (typically the main class). */
    public static SummerApplication run(Class<?> primarySource, String[] args) {
        long start = System.currentTimeMillis();
        Set<String> basePackages = resolveBasePackages(primarySource);
        Environment environment = new Environment();
        LoggingInitializer.initialize(environment);
        int port = environment.getProperty("server.port", Integer.class, 8080);
        String host = environment.getProperty("server.host", String.class, "0.0.0.0");
        printBanner();

        DefaultApplicationContext context = new DefaultApplicationContext(
                primarySource.getClassLoader(), environment, basePackages);
        registerAutoConfigurations(context);
        MapperRegistrar.registerDefinitions(context, basePackages);
        context.refresh();
        SummerUtil.setContext(context);

        WebRouteRegistrar.Registration registration = WebRouteRegistrar.build(context);
        Router router = registration.router();
        ExceptionHandlerRegistry exceptions = registration.exceptionHandlers();

        MessageConverter converter = resolveConverter(context);
        List<Filter> securityFilters = resolveSecurityFilters(context);
        HandlerMethodAccessChecker accessChecker = resolveAccessChecker(context);
        SummerWebServer server = new SummerWebServer(context, router, exceptions, converter,
                WebServerProperties.from(environment), securityFilters, accessChecker);
        WebSocketRegistry wsRegistry = new WebSocketRegistry();
        wsRegistry.scan(context);
        server.setWebSocketRegistry(wsRegistry);
        server.start();

        ScheduledTaskRegistrar scheduler = new ScheduledTaskRegistrar();
        scheduler.scheduleAll(context);
        SummerApplication app = new SummerApplication(context, server, scheduler);
        registerShutdownHook(app);

        invokeRunners(context, args);

        LOG.info("Started SummerApplication in " + (System.currentTimeMillis() - start)
                + "ms on " + host + ":" + port + " (base packages=" + basePackages + ")");
        return app;
    }

    /** Convenience entry that infers the primary source from the caller's main class. */
    public static SummerApplication run(String[] args) {
        Class<?> caller = StackWalker
                .getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.findFirst()
                        .map(f -> (Class<?>) f.getDeclaringClass())
                        .orElse(null));
        if (caller == null) {
            caller = SummerApplication.class;
        }
        return run(caller, args);
    }

    private static void registerAutoConfigurations(DefaultApplicationContext context) {
        for (Class<?> config : AUTO_CONFIG_CLASSES) {
            BeanDefinition def = new BeanDefinition(
                    DefaultApplicationContext.decapitalize(config.getSimpleName()), config);
            context.registerBeanDefinition(def.getName(), def);
        }
    }

    private static final java.util.List<Class<?>> AUTO_CONFIG_CLASSES =
            java.util.List.of(DataAutoConfiguration.class, SecurityAutoConfiguration.class);

    private static Set<String> resolveBasePackages(Class<?> primarySource) {
        Set<String> packages = new LinkedHashSet<>();
        SummerBootApplication app =
                AnnotationUtils.findAnnotation(primarySource, SummerBootApplication.class);
        if (app != null) {
            for (String pkg : app.scanBasePackages()) {
                if (!pkg.isBlank()) packages.add(pkg);
            }
            for (Class<?> marker : app.scanBasePackageClasses()) {
                packages.add(marker.getPackageName());
            }
        }
        ComponentScan scan = AnnotationUtils.findAnnotation(primarySource, ComponentScan.class);
        if (scan != null) {
            for (String pkg : scan.value()) if (!pkg.isBlank()) packages.add(pkg);
            for (String pkg : scan.basePackages()) if (!pkg.isBlank()) packages.add(pkg);
            for (Class<?> marker : scan.basePackageClasses()) packages.add(marker.getPackageName());
        }
        if (packages.isEmpty()) {
            packages.add(primarySource.getPackageName());
        }
        return packages;
    }

    private static MessageConverter resolveConverter(ApplicationContext context) {
        try {
            return context.getBean(MessageConverter.class);
        } catch (Exception e) {
            return new JsonMessageConverter();
        }
    }

    private static void registerShutdownHook(SummerApplication app) {
        Thread hook = new Thread(() -> {
            LOG.info("SummerApplication shutdown requested");
            app.scheduler.shutdown();
            app.webServer().stop();
            app.context().close();
        }, "summer-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
    }

    /**
     * Discover every {@link ApplicationRunner} bean in the context and invoke its
     * {@link ApplicationRunner#run} method, in ascending {@link Order @Order} sequence
     * (runners without {@code @Order} run last, preserving discovery order). Invoked
     * automatically by {@link #run(Class, String[])} once the web server is listening and
     * all beans are initialized; may also be called manually on an already-refreshed context.
     *
     * @throws IllegalStateException if any runner throws; later runners are skipped
     */
    public static void invokeRunners(ApplicationContext context, String[] args) {
        Map<String, ApplicationRunner> runners = context.getBeansOfType(ApplicationRunner.class);
        if (runners.isEmpty()) return;
        List<Map.Entry<String, ApplicationRunner>> entries = new ArrayList<>(runners.entrySet());
        entries.sort(Comparator.comparingInt(e -> orderOf(context, e.getKey())));
        ApplicationArguments appArgs = new DefaultApplicationArguments(args);
        for (Map.Entry<String, ApplicationRunner> e : entries) {
            try {
                e.getValue().run(appArgs);
            } catch (Throwable t) {
                throw new IllegalStateException("ApplicationRunner '" + e.getKey()
                        + "' failed to run: " + t.getMessage(), t);
            }
        }
    }

    private static int orderOf(ApplicationContext context, String name) {
        Class<?> type = context.getType(name);
        if (type == null) return Integer.MAX_VALUE;
        Order order = AnnotationUtils.findAnnotation(type, Order.class);
        return order != null ? order.value() : Integer.MAX_VALUE;
    }

    private static List<Filter> resolveSecurityFilters(ApplicationContext context) {
        try {
            SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
            return chain.filters();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static HandlerMethodAccessChecker resolveAccessChecker(ApplicationContext context) {
        try {
            return context.getBean(HandlerMethodAccessChecker.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static void printBanner() {
        LOG.info("""
            \n ___ _   _ _ __ ___  _ __ ___   ___ _ __\s
            / __| | | | '_ ` _ \\| '_ ` _ \\ / _ \\ '__|
            \\__ \\ |_| | | | | | | | | | | |  __/ |  \s
            |___/\\__,_|_| |_| |_|_| |_| |_|\\___|_|  \s
            :: v3.0.0 ::
                """);
    }
}