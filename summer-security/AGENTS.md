# summer-security 模块约定

> **按需加载**：仅当 AI agent 读取 `summer-security/` 目录下的文件时才会加载本文件。

## 模块职责

JWT 无状态认证 + BCrypt + URL/方法级授权 + CSRF 防护。依赖 summer-core 和 summer-web。纯 JDK 实现，零第三方依赖。

## 关键包

| 包路径 | 职责 |
| --- | --- |
| `cn.jiebaba.summe.security.web` | Web 安全：SecurityFilterChain/HttpSecurity + JWT 过滤器（登录/认证/刷新） + 授权过滤器 + 方法级安全 |
| `cn.jiebaba.summe.security.jwt` | JWT：JwtEncoder/JwtDecoder/JwtClaims，自研 JSON 读写 |
| `cn.jiebaba.summe.security.crypto` | 密码编码：PasswordEncoder + BCrypt + NoOp |
| `cn.jiebaba.summe.security.authentication` | 认证：AuthenticationManager/ProviderManager/DaoAuthenticationProvider |
| `cn.jiebaba.summe.security.authorization` | 授权：AccessDeniedException |
| `cn.jiebaba.summe.security.userdetails` | 用户详情：UserDetailsService/InMemoryUserDetailsManager/User |
| `cn.jiebaba.summe.security.core` | 安全上下文：SecurityContext/SecurityContextHolder/Authentication |
| `cn.jiebaba.summe.security.web.csrf` | CSRF：CsrfFilter/CsrfToken/CookieCsrfTokenRepository |
| `cn.jiebaba.summe.security.annotation` | 注解：@AuthenticationPrincipal/@PreAuthorize/@DenyAll/@PermitAll |

## 模块约定

- **零第三方依赖**：JWT 自研实现（纯 JDK Base64 + HMAC/RSA），BCrypt 自研实现，JSON 自研实现
- **过滤器链**：JwtLoginFilter → JwtAuthenticationFilter → AuthorizationFilter，按顺序执行
- **方法级授权**：`MethodSecurityEnforcer` 配合 `@PreAuthorize` 注解，在方法调用前检查权限
- **无状态**：不使用 Session，完全基于 JWT token
