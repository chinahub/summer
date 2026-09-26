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
import cn.jiebaba.summer.boot.event.ApplicationFailedEvent;
import cn.jiebaba.summer.boot.event.ApplicationReadyEvent;
import cn.jiebaba.summer.boot.security.SecurityAutoConfiguration;
import cn.jiebaba.summer.boot.web.WebAutoConfiguration;
import cn.jiebaba.summer.boot.office.OfficeAutoConfiguration;
import cn.jiebaba.summer.boot.data.MapperRegistrar;
import cn.jiebaba.summer.core.context.BeanDefinition;
import cn.jiebaba.summer.web.support.WebRouteRegistrar;
import cn.jiebaba.summer.web.bind.HandlerMethodAccessChecker;
import cn.jiebaba.summer.web.filter.Filter;
import cn.jiebaba.summer.web.filter.FilterChainSelector;
import cn.jiebaba.summer.web.resource.StaticResourceHandler;
import java.util.List;
import cn.jiebaba.summer.security.authentication.AuthenticationManager;
import cn.jiebaba.summer.security.jwt.JwtDecoder;
import cn.jiebaba.summer.security.jwt.JwtEncoder;
import cn.jiebaba.summer.security.web.SecurityFilterChain;
import cn.jiebaba.summer.security.web.csrf.CsrfProperties;
import cn.jiebaba.summer.web.websocket.WebSocketRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.io.IOException;
import java.io.InputStream;
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

    /** 以显式主源（通常是主类）运行。 */
    public static SummerApplication run(Class<?> primarySource, String[] args) {
        long start = System.currentTimeMillis();
        Set<String> basePackages = resolveBasePackages(primarySource);
        Environment environment = new Environment(args);
        LoggingInitializer.initialize(environment);
        if (!environment.getActiveProfiles().isEmpty()) {
            LOG.info("The following profiles are active: " + environment.getActiveProfiles());
        }
        int port = environment.getProperty("server.port", Integer.class, 8080);
        String host = environment.getProperty("server.host", String.class, "0.0.0.0");
        printBanner();
        LOG.info("with PID " + ProcessHandle.current().pid());

        DefaultApplicationContext context = null;
        SummerWebServer server = null;
        boolean shutdownHookRegistered = false;
        String stage = "config";
        try {
            stage = "container";
            context = new DefaultApplicationContext(
                    primarySource.getClassLoader(), environment, basePackages);
            registerAutoConfigurations(context);
            MapperRegistrar.registerDefinitions(context, basePackages);
            context.refresh();
            SummerUtil.setContext(context);
            stage = "web";
            WebRouteRegistrar.Registration registration = WebRouteRegistrar.build(context);
            Router router = registration.router();
            ExceptionHandlerRegistry exceptions = registration.exceptionHandlers();

            MessageConverter converter = resolveConverter(context);
            List<Filter> webFilters = new ArrayList<>(context.getBeansOfType(Filter.class).values());
            List<SecurityFilterChain> securityChains = resolveSecurityChains(context);
            HandlerMethodAccessChecker accessChecker = resolveAccessChecker(context);
            server = new SummerWebServer(context, router, exceptions, converter,
                    WebServerProperties.from(environment), webFilters, accessChecker);
            if (!securityChains.isEmpty()) {
                server.setFilterChainSelector(buildFilterChainSelector(webFilters, securityChains));
            }
            WebSocketRegistry wsRegistry = new WebSocketRegistry();
            wsRegistry.scan(context);
            server.setWebSocketRegistry(wsRegistry);
            StaticResourceHandler statics = resolveStaticHandler(context);
            if (statics != null) {
                server.setStaticResourceHandler(statics);
            }
            server.start();

            ScheduledTaskRegistrar scheduler = new ScheduledTaskRegistrar();
            scheduler.scheduleAll(context);
            SummerApplication app = new SummerApplication(context, server, scheduler);
            registerShutdownHook(app);
            shutdownHookRegistered = true;

            stage = "runners";
            invokeRunners(context, args);

            context.publishEvent(new ApplicationReadyEvent(context));
            LOG.info("Started SummerApplication in " + (System.currentTimeMillis() - start)
                    + "ms on " + host + ":" + port + " (base packages=" + basePackages + ")");
            return app;
        } catch (Throwable t) {
            // 启动失败诊断：输出阶段 + 根因摘要；关闭钩子未注册时回收已创建的资源（容器/服务器）
            LOG.severe(buildFailureReport(start, stage, t));
            if (context != null && shutdownHookRegistered) {
                try {
                    context.publishEvent(new ApplicationFailedEvent(context, stage, t));
                } catch (Throwable ignored) {
                    // 失败事件监听器异常不掩盖原始失败
                }
            }
            if (!shutdownHookRegistered) {
                if (server != null) {
                    try {
                        server.stop();
                    } catch (Throwable ignored) {
                        // 清理失败不影响失败报告输出
                    }
                }
                if (context != null) {
                    try {
                        context.close();
                    } catch (Throwable ignored) {
                        // 清理失败不影响失败报告输出
                    }
                }
            }
            throw t;
        }
    }

    /** 便捷入口：从调用方主类推断主源。 */
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
        List<Class<?>> configs = new ArrayList<>(AUTO_CONFIG_CLASSES);
        // SPI 扩展：从 classpath 根 META-INF/summer.factories 读取第三方自动配置类
        configs.addAll(loadSpiConfigurations());
        // 可选模块 summer-ai：仅当其在 classpath 时注册自动配置。仿 spring-boot 的
        // @ConditionalOnClass，但用存在性探测代替 ASM 读注解，零字节码第三方库依赖；
        // summer-ai 不在时其自动配置类永不被加载，故不会 NoClassDefFoundError。
        // 注：AiAutoConfiguration 随 summer-ai 模块发布（包名仍为 cn.jiebaba.summer.boot.ai），
        // 这里用反射注册以避免 summer-boot 对 summer-ai 的编译期依赖（否则两模块循环依赖）。
        if (isClassPresent("cn.jiebaba.summer.ai.chat.ChatModel")) {
            addOptionalConfig(configs, "cn.jiebaba.summer.boot.ai.AiAutoConfiguration");
        }
        // 可选模块 summer-ai A2A 跨实例协作：仅当其在 classpath 时注册自动配置
        //（summer.ai.a2a.enabled=true 才实际装配 bean，见 A2aAutoConfiguration 上的条件注解）。
        if (isClassPresent("cn.jiebaba.summer.ai.agent.A2aCoordinator")) {
            addOptionalConfig(configs, "cn.jiebaba.summer.boot.ai.A2aAutoConfiguration");
        }
        // summer-support OCR：仅当 OCR 类在 classpath 时，反射注册其自动配置
        //（summer-boot 不依赖 summer-support，避免循环依赖）。
        if (isClassPresent("cn.jiebaba.summer.support.ocr.Ocr")) {
            addOptionalConfig(configs, "cn.jiebaba.summer.support.ocr.OcrAutoConfiguration");
        }
        for (Class<?> config : configs) {
            BeanDefinition def = new BeanDefinition(
                    DefaultApplicationContext.decapitalize(config.getSimpleName()), config);
            context.registerBeanDefinition(def.getName(), def);
        }
    }

    private static final java.util.List<Class<?>> AUTO_CONFIG_CLASSES =
            java.util.List.of(DataAutoConfiguration.class, SecurityAutoConfiguration.class,
                    WebAutoConfiguration.class, OfficeAutoConfiguration.class);

    /**
     * 反射加载可选模块的自动配置类并加入注册列表：类不在 classpath（可选依赖未引入）时跳过。
     * 用于 summer-ai / summer-support 等 summer-boot 不能编译期依赖的模块。
     */
    private static void addOptionalConfig(List<Class<?>> configs, String className) {
        try {
            configs.add(Class.forName(className, false, SummerApplication.class.getClassLoader()));
        } catch (Throwable e) {
            LOG.fine("Skipped optional auto-configuration (class not found): " + className);
        }
    }

    /** 探测类是否在 classpath（不初始化），用于可选自动配置的条件激活。 */
    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, SummerApplication.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 从 classpath 根 {@code META-INF/summer.factories} 读取第三方自动配置类：
     * 每行一个全限定类名；兼容 Spring Boot 风格的 {@code key=类名1,类名2}（key 忽略）。
     * {@code #} 注释与空行忽略；类不在 classpath（可选依赖未引入）时跳过并记录日志，
     * 非法类名或非 @Configuration 类同样跳过。重复类名去重。
     */
    private static List<Class<?>> loadSpiConfigurations() {
        Set<String> seen = new LinkedHashSet<>();
        try {
            var urls = SummerApplication.class.getClassLoader()
                    .getResources("META-INF/summer.factories");
            while (urls.hasMoreElements()) {
                parseSpiFactories(urls.nextElement(), seen);
            }
        } catch (IOException e) {
            LOG.fine("Failed to read META-INF/summer.factories: " + e.getMessage());
        }
        List<Class<?>> configs = new ArrayList<>();
        for (String name : seen) {
            try {
                Class<?> clazz = Class.forName(name, false, SummerApplication.class.getClassLoader());
                if (!AnnotationUtils.hasAnnotation(clazz, cn.jiebaba.summer.core.annotation.Configuration.class)) {
                    LOG.warning("Skipped summer.factories entry (not a @Configuration): " + name);
                    continue;
                }
                configs.add(clazz);
            } catch (Throwable e) {
                LOG.warning("Skipped summer.factories entry (class not found): " + name);
            }
        }
        if (!configs.isEmpty()) {
            LOG.info("Loaded " + configs.size() + " auto-configuration(s) from summer.factories: " + seen);
        }
        return configs;
    }

    /** 解析单个 summer.factories 资源为全限定类名集合。 */
    private static void parseSpiFactories(java.net.URL url, Set<String> out) {
        try (InputStream in = url.openStream()) {
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String rawLine : text.split("\r?\n")) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                String body = eq >= 0 ? line.substring(eq + 1) : line;
                for (String name : body.split(",")) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) out.add(trimmed);
                }
            }
        } catch (IOException e) {
            LOG.fine("Failed to parse " + url + ": " + e.getMessage());
        }
    }

    /**
     * 解析组件扫描的基础包：依次从 {@link SummerBootApplication} 与 {@link ComponentScan}
     * 中收集 {@code scanBasePackages}/{@code scanBasePackageClasses}；若均为空则回退到主源所在包。
     *
     * @param primarySource 主源类
     * @return 需扫描的包名集合
     */
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
            // 各步独立捕获异常，确保前一步失败不阻断后续资源释放（参考 Spring Boot 关闭钩子）。
            try {
                app.scheduler.shutdown();
            } catch (Throwable t) {
                LOG.warning("Error stopping scheduler: " + t.getMessage());
            }
            try {
                app.webServer().stop();
            } catch (Throwable t) {
                LOG.warning("Error stopping web server: " + t.getMessage());
            }
            try {
                app.context().close();
            } catch (Throwable t) {
                LOG.warning("Error closing application context: " + t.getMessage());
            }
        }, "summer-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
    }

    /**
     * 发现上下文中的每个 {@link ApplicationRunner} Bean，并按 {@link Order @Order} 升序
     * 调用其 {@link ApplicationRunner#run} 方法（未设置 {@code @Order} 的 runner 排在最后，
     * 保持发现顺序）。在 Web 服务器开始监听且所有 Bean 初始化完成后，由
     * {@link #run(Class, String[])} 自动调用；也可在已刷新的上下文上手动调用。
     *
     * @throws IllegalStateException 若任一 runner 抛出异常；后续 runner 将被跳过
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

    /**
     * 解析安全过滤器链：收集上下文中所有用户定义的 {@link SecurityFilterChain} Bean，
     * 按 {@code order()} 升序（稳定）排序后返回；若用户未定义任何链，则依据
     * {@code summer.security.*} / {@code summer.security.csrf.*} 构建默认链。
     *
     * @param context 应用上下文
     * @return 有序的安全过滤器链列表（可能为空，表示无安全配置）
     */
    private static List<SecurityFilterChain> resolveSecurityChains(ApplicationContext context) {
        Map<String, SecurityFilterChain> beans = context.getBeansOfType(SecurityFilterChain.class);
        if (!beans.isEmpty()) {
            List<SecurityFilterChain> chains = new ArrayList<>(beans.values());
            chains.sort(Comparator.comparingInt(SecurityFilterChain::order));
            return chains;
        }
        // 无用户链：构建默认链（依据 summer.security.* / summer.security.csrf.* 配置）
        try {
            Environment env = context.getEnvironment();
            CsrfProperties csrf = context.getBean(CsrfProperties.class);
            AuthenticationManager auth = context.getBean(AuthenticationManager.class);
            JwtEncoder encoder = context.getBean(JwtEncoder.class);
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            SecurityFilterChain chain =
                    SecurityAutoConfiguration.buildDefaultSecurityFilterChain(env, csrf, auth, encoder, decoder);
            return chain.isEnabled() ? List.of(chain) : List.of();
        } catch (Exception e) {
            // 缺少必要组件（如安全模块未启用）：视为无安全配置
            return List.of();
        }
    }

    /**
     * 构建按请求分发多链的选择器：对每个请求先应用 Web 层过滤器（如 CORS），
     * 再选取第一条 {@link SecurityFilterChain#matches 匹配} 的链的过滤器；若无链匹配则仅 Web 层过滤器。
     *
     * @param webFilters Web 层过滤器（所有请求恒定应用）
     * @param chains     按 order 升序的安全过滤器链
     * @return 按请求选择过滤器列表的选择器
     */
    private static FilterChainSelector buildFilterChainSelector(List<Filter> webFilters,
                                                                List<SecurityFilterChain> chains) {
        List<Filter> base = List.copyOf(webFilters);
        List<SecurityFilterChain> ordered = List.copyOf(chains);
        return request -> {
            List<Filter> filters = new ArrayList<>(base);
            for (SecurityFilterChain chain : ordered) {
                if (chain.matches(request)) {
                    filters.addAll(chain.filters());
                    return filters;
                }
            }
            return filters;
        };
    }

    private static HandlerMethodAccessChecker resolveAccessChecker(ApplicationContext context) {
        try {
            return context.getBean(HandlerMethodAccessChecker.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析静态资源处理器 Bean（未注册时返回 null：不启用静态资源回退）。 */
    private static StaticResourceHandler resolveStaticHandler(ApplicationContext context) {
        try {
            return context.getBean(StaticResourceHandler.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建启动失败的诊断报告（对齐 Spring Boot 的 APPLICATION FAILED TO START 风格）：
     * 输出失败阶段、耗时、异常类型与消息、根因链及行动建议。纯文本生成，无副作用。
     *
     * @param startMillis 启动起始毫秒（用于耗时统计）
     * @param stage       失败阶段：config/container/web/runners
     * @param failure     启动过程中抛出的异常
     */
    public static String buildFailureReport(long startMillis, String stage, Throwable failure) {
        StringBuilder sb = new StringBuilder(512);
        sb.append('\n').append("***************************").append('\n');
        sb.append("APPLICATION FAILED TO START").append('\n');
        sb.append("***************************").append('\n');
        sb.append("Stage: ").append(stage).append('\n');
        sb.append("Time: ").append(System.currentTimeMillis() - startMillis).append("ms").append('\n');
        sb.append("Error: ").append(failure.getClass().getName()).append(": ")
                .append(failure.getMessage()).append('\n');
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != failure) {
            sb.append("Root cause: ").append(root.getClass().getName()).append(": ")
                    .append(root.getMessage()).append('\n');
        }
        sb.append("Action: 请检查上方错误信息中的配置指引（summer.* 配置项）后重新启动。");
        return sb.toString();
    }

    private static void printBanner() {
        LOG.info("""
            \n ___ _   _ _ __ ___  _ __ ___   ___ _ __\s
            / __| | | | '_ ` _ \\| '_ ` _ \\ / _ \\ '__|
            \\__ \\ |_| | | | | | | | | | | |  __/ |  \s
            |___/\\__,_|_| |_| |_|_| |_| |_|\\___|_|  \s
            :: v%s ::
                """.formatted(resolveVersion()));
    }

    /**
     * 从 jar 内 Maven 自动生成的 {@code pom.properties} 解析框架版本号，
     * 供启动 banner 显示；读取失败时（如 IDE 中直接运行未打包的 classes）回退为 {@code dev}。
     */
    private static String resolveVersion() {
        try (InputStream in = SummerApplication.class.getResourceAsStream(
                "/META-INF/maven/cn.jiebaba.summer/summer-boot/pom.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String version = props.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version;
                }
            }
        } catch (IOException ignored) {
        }
        return "dev";
    }
}
