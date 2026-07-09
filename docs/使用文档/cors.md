# 跨域（CORS）

Summer 内置 CORS（跨源资源共享）支持，纯 JDK 实现，零第三方依赖，参考 Spring 的 `CorsConfiguration` / `CorsFilter` 设计。通过 `summer.web.cors.*` 配置，由 `summer-boot` 自动装配的 `CorsFilter` 在路由分派之前处理跨域请求。

## 设计要点

- **过滤器实现**：`CorsFilter` 实现 summer-web 的 `Filter` 接口，在 `RequestDispatcher` 的前置过滤器链中执行，先于安全过滤器，使预检请求不触发认证。
- **预检短路**：对 OPTIONS 预检请求（携带 `Origin` 与 `Access-Control-Request-Method`）直接返回 204 与预检响应头，不进入路由。
- **实际请求补头**：对通过预检的实际请求补充 `Access-Control-Allow-Origin` 等响应头后继续分派。
- **来源匹配**：支持精确来源、通配 `*`、以及 `allowed-origin-patterns` 通配模式（如 `https://*.example.com`）。
- **凭证兼容**：启用 `allow-credentials` 时回显具体来源（CORS 规范不允许 `*` 与凭证同时使用）。
- **自动装配**：`summer-boot` 的 `WebAutoConfiguration` 注册 `CorsProperties` 与 `CorsFilter`，引入 summer-boot 即生效；未启用时 `CorsFilter` 透传，对现有应用无影响。

## 配置

```yaml
summer:
  web:
    cors:
      enabled: true                       # 启用 CORS（默认 false）
      allowed-origins:                    # 允许的来源；* 表示全部
        - https://example.com
        - https://*.example.com           # 注意：* 在 origins 中仅作通配字面量，
                                          #       子域通配请用 allowed-origin-patterns
      allowed-origin-patterns:            # 通配模式（支持 * 子串匹配）
        - https://*.example.com
      allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
      allowed-headers: [Content-Type, Authorization]
      exposed-headers: [X-Custom-Header]
      allow-credentials: true
      max-age: 1800                       # 预检缓存时长（秒，默认 1800）
```

等价的 `application.properties` 写法（列表用逗号分隔）：

```properties
summer.web.cors.enabled=true
summer.web.cors.allowed-origins=https://example.com,https://app.example.com
summer.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
summer.web.cors.allowed-headers=Content-Type,Authorization
summer.web.cors.allow-credentials=true
summer.web.cors.max-age=1800
```

## 行为说明

| 场景 | 行为 |
| --- | --- |
| 未启用（`enabled=false`） | 透传至后续过滤器，不添加任何 CORS 头 |
| 请求无 `Origin` 头 | 视为同源/非跨域请求，直接放行 |
| 来源未获允许 | 返回 403，不附带 CORS 头（浏览器将阻止读取响应） |
| 预检请求（OPTIONS + ACRM） | 返回 204，补充预检响应头，不进入路由 |
| 实际跨域请求 | 补充 CORS 响应头后继续分派至路由 |
| 启用但未配置任何来源/模式 | 默认放行全部来源（便于开发），凭证模式下回显具体来源 |

> 预检响应中的 `Access-Control-Allow-Headers` 在未配置 `allowed-headers` 时回显预检请求的 `Access-Control-Request-Headers`，最大兼容浏览器请求的任意头。

## 使用示例

启用 CORS 后，业务控制器无需任何改动即可被跨域访问：

```java
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "hello from summer");
        return result;
    }
}
```

`curl` 预检测试：

```bash
curl -i -X OPTIONS http://localhost:8080/hello \
  -H "Origin: https://example.com" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: Authorization"
```

预期响应包含：

```
HTTP/1.1 204 No Content
Access-Control-Allow-Origin: https://example.com
Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS
Access-Control-Allow-Headers: Authorization
Access-Control-Max-Age: 1800
Vary: Origin, Access-Control-Request-Headers
```

## 注意事项

- CORS 仅约束浏览器行为，不替代服务端鉴权；跨域放行后仍会经过安全过滤器（预检除外）。
- 生产环境建议显式配置 `allowed-origins` / `allowed-origin-patterns`，避免使用 `*` 放行全部来源。
- 启用 `allow-credentials` 时不能返回 `*`，框架会自动回显具体来源。
- CSRF 过滤器尚未实现（见 roadmap），当前阶段仅提供 CORS。
