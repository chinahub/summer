# AOP 面向切面（summer-core）

基于 JDK 动态代理与手写字节码子类代理 + 拦截器链实现的最小 AOP，零第三方依赖。对被代理 Bean 的方法调用织入通知（advice），支持 `@Around/@Before/@After/@AfterReturning/@AfterThrowing`。

## 注解

| 注解 | 作用域 | 说明 |
| --- | --- | --- |
| `@Aspect` | 类 | 声明切面类（元标注 `@Component`，自动注册为 bean） |
| `@Pointcut("execution(...)")` | 方法 | 声明可复用切点（方法体留空） |
| `@Around("execution(...)")` | 方法 | 环绕通知，需调用 `jp.proceed()` 才执行目标 |
| `@Before("execution(...)")` | 方法 | 前置通知 |
| `@After("execution(...)")` | 方法 | 后置通知（无论是否异常都执行） |
| `@AfterReturning(value="...", returning="ret")` | 方法 | 返回通知，可绑定返回值 |
| `@AfterThrowing(value="...", throwing="ex")` | 方法 | 异常通知，可绑定异常 |

通知方法可直接写切点表达式，也可引用 `@Pointcut` 方法名。

## 切点表达式

当前支持 `execution()` 一种表达式，格式：

```
execution(修饰符 返回类型 包名..类型.方法(参数))
```

- `*` 匹配任意；
- `..` 在包路径中表示「含子包」，在参数列表中表示「任意参数」；
- 示例：`execution(* cn.jiebaba.summer.sample.repository..*.*(..))` 匹配 `repository` 包及子包下所有类的所有方法。

## JoinPoint / ProceedingJoinPoint

```java
public interface JoinPoint {
    Object getThis();        // 代理对象
    Object getTarget();      // 目标对象
    Method getMethod();      // 被拦截方法
    Object[] getArgs();      // 实参
    String getSignature();   // 签名描述
}
```

`ProceedingJoinPoint`（仅 `@Around` 可用）额外提供：

```java
Object proceed() throws Throwable;        // 用原参数执行目标
Object proceed(Object[] args) throws Throwable;  // 替换参数执行
```

## 示例

```java
@Aspect
public class LoggingAspect {
    private static final Logger LOG = Logger.getLogger(LoggingAspect.class.getName());

    @Around("execution(* cn.jiebaba.summer.sample.repository..*.*(..))")
    public Object logAround(ProceedingJoinPoint jp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = jp.proceed();
        LOG.info("[" + jp.getSignature() + "] took "
                + (System.currentTimeMillis() - start) + "ms");
        return result;
    }

    @AfterReturning("execution(* cn.jiebaba.summer.sample.repository..*.save(..))")
    public void afterSave(ProceedingJoinPoint jp) {
        LOG.info("save completed on " + jp.getSignature());
    }
}
```

## 实现原理

- 容器在 `preInstantiateSingletons` 阶段**先**实例化所有 `@Aspect` bean 并收集通知为 `ProxyAdvisor`；
- 每个单例 bean 创建时判断是否需要代理：若其任一方法命中某切点或 `ProxyAdvisor`，则自动选择代理策略——目标类实现了接口用 `AdvisedProxyFactory` 生成 JDK 动态代理；未实现接口且可被继承（非 `final`、通过构造器实例化）时用 `SubclassProxyFactory` 手写字节码生成子类代理（`$$summer$super$<方法>` 桥接方法以 `invokespecial super` 破解自调用递归），把匹配的 advisor/通知组成拦截器链；
- 调用代理方法时按 `Around → Before → 目标 → AfterReturning/AfterThrowing → After` 顺序执行；
- `@Transactional` 即基于同一拦截器链注册 `TransactionInterceptor` 实现。

## 限制

- 代理策略自动二选一：目标类实现接口走 JDK 动态代理；无接口走手写字节码子类代理（零依赖，非 CGLIB）；
- 子类代理要求目标类非 `final`、通过构造器实例化（`@Bean` 工厂方法 / `instanceSupplier` 产生的无接口 bean 暂不支持子类代理，需提取接口）；仅拦截 public/protected 非 final 方法（private、static、final 方法不拦截）；`final` 类无法子类代理；
- 子类代理为单对象模型（代理实例即 bean 本身），`getThis()` 与 `getTarget()` 指向同一代理；方法内的自调用会被拦截；应避免在构造器中调用可被拦截的方法（构造期拦截尚未就绪）；
- 切点表达式仅支持 `execution()`，不支持 `@annotation`、`bean()` 等；
- 通知方法需与切点签名兼容（`@Around`/`@AfterReturning` 可绑定参数）。
