# Summer Security

Summer 提供了一套参考 Spring Security 的安全模块 summer-security，纯 JDK 实现（零第三方依赖），支持 JWT 无状态认证、URL 级 + 方法级授权、BCrypt 密码编码。

## 快速开始

### 1. 启用安全（opt-in）

在 application.yml 中配置：

```yaml
summer:
  security:
    enabled: true                      # 默认关闭，opt-in
    jwt:
      secret: your-256-bit-secret-here-must-be-32-bytes!   # 至少 32 字节
      access-token-ttl: 3600           # token 有效期（秒）
      login-url: /login                # 登录端点
    password:
      bcrypt:
        strength: 10                   # BCrypt cost factor (4-31)
    users:                             # 内存用户（可选，也可自定义 UserDetailsService）
      admin:
        password: $2a$10$...           # BCrypt 哈希
        roles: ADMIN,USER
      user:
        password: $2a$10$...
        roles: USER
```

未配置 summer.security.jwt.secret 时，启动会生成临时密钥并 WARN。生产环境务必配置固定密钥。

### 2. 登录获取 Token

```
POST /login
Content-Type: application/json

{"username":"admin","password":"admin123"}
```

响应：

```json
{
  "accessToken": "eyJhbGci...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "username": "admin",
  "authorities": ["ROLE_ADMIN","ROLE_USER"]
}
```

### 3. 携带 Token 访问受保护资源

```
GET /me
Authorization: Bearer eyJhbGci...
```

## 授权配置

### URL 级授权

通过 HttpSecurity DSL 配置（在 @Configuration 类中定义 @Bean SecurityFilterChain）：

```java
@Configuration
public class SecurityConfig {

    @Bean("mySecurityFilterChain")
    @Primary
    public SecurityFilterChain filterChain(AuthenticationManager am,
                                           JwtEncoder enc, JwtDecoder dec) {
        return HttpSecurity.security()
            .authorize(
                HttpSecurity.match("/public/**").permitAll(),
                HttpSecurity.match("/admin/**").hasRole("ADMIN"),
                HttpSecurity.match("/api/**").hasAuthority("api:read"),
                HttpSecurity.anyRequest().authenticated())
            .jwt(jwt -> jwt
                .encoder(enc)
                .decoder(dec)
                .authenticationManager(am)
                .loginUrl("/login")
                .tokenTtl(3600))
            .build();
    }
}
```

支持的规则：
- permitAll() — 无需认证
- authenticated() — 需要认证（任意角色）
- hasRole("ADMIN") / hasAnyRole("ADMIN","USER") — 需要指定角色
- hasAuthority("api:read") / hasAnyAuthority(...) — 需要指定权限
- denyAll() — 拒绝所有

规则按声明顺序匹配，第一个匹配的生效。anyRequest() 作为兜底默认规则。

### 方法级授权

在 Controller 方法上使用注解，在路由匹配后、方法调用前强制：

```java
@RestController
public class AdminController {

    @GetMapping("/secret")
    @PreAuthorize(roles = {"ADMIN"})           // 需要 ADMIN 角色
    public Map<String, Object> secret() { ... }

    @GetMapping("/manage")
    @PreAuthorize(roles = {"ADMIN","USER"}, requireAll = false)  // ADMIN 或 USER
    public Map<String, Object> manage() { ... }

    @GetMapping("/public/hello")
    @PermitAll                                  // 放行，无需认证
    public Map<String, String> hello() { ... }

    @GetMapping("/internal")
    @DenyAll                                    // 拒绝所有
    public Map<String, String> internal() { ... }
}
```

注解说明：
- @PreAuthorize(roles={}, authorities={}, requireAll=true) — 声明式授权；requireAll=true 要求全部满足，false 要求任一满足
- @PermitAll — 放行
- @DenyAll — 拒绝

方法注解优先于类注解。

注意：方法级 @PreAuthorize 仅作用于 Web 处理器方法（Controller 方法）。服务层方法级安全需先增强字节码代理以复制注解，计划在后续版本支持。

## 注入认证信息

在 Controller 方法参数上使用 @AuthenticationPrincipal 注入当前用户：

```java
@GetMapping("/me")
public Map<String, Object> me(@AuthenticationPrincipal UserDetails principal) {
    // principal 是 UserDetails 实例（自动从 UserDetailsService 重新加载）
    return Map.of("username", principal.getUsername());
}
```

也可直接注入 Authentication 或 SecurityContext：

```java
@GetMapping("/whoami")
public String whoami(Authentication auth) {
    return auth.getName();
}
```

## 自定义用户存储

实现 UserDetailsService 接口，用 @Bean("myUserDetailsService") @Primary 覆盖默认的内存实现：

```java
@Bean("dbUserDetailsService")
@Primary
public UserDetailsService userDetailsService(SqlExecutor executor) {
    return username -> {
        // 从数据库查询用户
        User user = executor.queryOne("SELECT * FROM users WHERE username = ?", ...);
        return User.withRoles(user.username(), user.password(), user.roles().split(","));
    };
}
```

## 密码编码

```java
@Autowired
private PasswordEncoder passwordEncoder;

// 编码
String hash = passwordEncoder.encode("mypassword");

// 校验
boolean ok = passwordEncoder.matches("mypassword", hash);
```

BCryptPasswordEncoder 生成 $2a$cost$salt+hash 格式，与 Spring Security 和大多数 bcrypt 库兼容。

## 核心架构

```
请求 → SummerWebServer
         → RequestDispatcher
              → SecurityFilterChain (summer-security)
                   → JwtLoginFilter     (POST /login → 颁发 token)
                   → JwtAuthenticationFilter (解析 Bearer token → SecurityContext)
                   → AuthorizationFilter (URL 级规则匹配)
              → [路由匹配]
              → MethodSecurityEnforcer (方法级 @PreAuthorize 检查)
              → HandlerMethodInvoker (参数绑定 + @AuthenticationPrincipal 注入)
              → Controller 方法
```

- SecurityContextHolder：基于 ThreadLocal，请求结束自动清理，适配虚拟线程。
- opt-in：summer.security.enabled=false（默认）时，过滤器链为空、方法检查器为 no-op，对现有应用零影响。
- 纯 JDK：JWT 用 javax.crypto.Mac (HS256)，BCrypt 手写 Blowfish/EksBlowfish，无第三方库。