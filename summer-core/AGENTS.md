# summer-core 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-core/` 目录下的文件时才会加载本文件。

## 模块职责

summer 框架最底层核心模块（4.0 从 summer-boot 拆出，包名 `cn.jiebaba.summer.core` 不变）：IoC 容器（context）、类扫描（scanner）、AOP（aop + 自研字节码引擎 aop.bytecode）、JUL 日志 + SLF4J 绑定（logging/logging.slf4j）、定时任务（scheduling）、环境配置（env）、自研 JSON（json）、工具集（util）、框架注解（annotation）、测试微框架（test）、ONNX 引擎（onnx）。summer-boot（自动配置 + web/data/security/office）与 summer-support（OCR）的共同底层，被 boot 传递依赖、被 support 直接依赖。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summer.core` | `SummerException`：框架统一基础运行期异常（各模块异常的根，OcrException 等继承它） |
| `cn.jiebaba.summer.core.annotation` | 构造型与 DI 注解：`@Component/@Service/@Repository/@Controller/@Configuration`、`@Bean/@Autowired/@Value/@Scope/@Qualifier/@Primary/@Lazy/@PostConstruct/@PreDestroy/@ComponentScan/@Order` + 条件注解（`@ConditionalOnClass/@ConditionalOnMissingBean/@ConditionalOnProperty`） |
| `cn.jiebaba.summer.core.context` | `ApplicationContext` 接口、`DefaultApplicationContext`（IoC 容器实现，含 `publishEvent` 事件发布与 `@EventListener` 监听、`@ConfigurationProperties` 绑定）、`BeanDefinition`、生命周期接口 |
| `cn.jiebaba.summer.core.env` | `Environment`：属性加载（基础 + `application-{profile}` 叠加 + 环境变量按需 + 命令行）、`${key:default}` 占位符解析、类型转换；`YamlParser` 解析 `application.yml`（含 `---` 多文档） |
| `cn.jiebaba.summer.core.scanner` | `ClassPathScanner`（类路径类扫描）、`AnnotationUtils`（元注解递归查找） |
| `cn.jiebaba.summer.core.aop` | `@Aspect/@Pointcut/@Around/@Before/@After/@AfterReturning/@AfterThrowing`、`PointcutMatcher`、`AdvisedProxyFactory`（JDK 动态代理）、`SubclassProxyFactory`（自研字节码子类代理 aop.bytecode：ConstantPool/ClassBuilder/MethodBuilder/Descriptor）、`SummerProxy`、`JoinPoint/ProceedingJoinPoint` |
| `cn.jiebaba.summer.core.logging` | JUL 日志（DailyRollingFileHandler/SingleLineFormatter）+ SLF4J 绑定（logging.slf4j：SummerSlf4jServiceProvider，`META-INF/services/org.slf4j.spi.SLF4JServiceProvider` 注册文件随本模块发布）+ MDC 适配器 |
| `cn.jiebaba.summer.core.scheduling` | `@Scheduled`（cron/fixedRate/fixedDelay）、`CronExpression`、`ScheduledTaskRegistrar`（定时线程池 + 虚拟线程） |
| `cn.jiebaba.summer.core.json` / `util` | 自研 JSON（`Json`）与 `JsonUtil`（JSONObject/JSONArray）、通用工具集 |
| `cn.jiebaba.summer.core.onnx` | `OnnxEngine`/`OnnxException`：FFM 直连 onnxruntime 原生库的推理引擎（summer-support OCR 使用） |
| `cn.jiebaba.summer.core.test` | `@SummerTest`/`SummerExtension`：JUnit 5 测试微框架（JUnit API 为 optional 依赖） |

## 模块约定

- **依赖方向**：本模块是最底层，不依赖任何 summer-* 模块；summer-boot 与 summer-support 依赖本模块
- **零第三方运行时依赖**：仅 SLF4J API 与 JUnit API 为 optional 依赖（SLF4J→JUL 绑定与 @SummerTest 测试微框架），不传递给使用者
- **包名不变**：从 summer-boot 拆出时所有类保持 `cn.jiebaba.summer.core.*` 包名，使用方 import 无需改动
- **AOP 代理**：使用自研字节码引擎（`aop/bytecode/`），不依赖 CGLIB 或 ByteBuddy；接口用 JDK 动态代理，无接口类用类代理
