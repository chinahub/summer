package cn.jiebaba.summer.test.multichain;

import cn.jiebaba.summer.security.annotation.AuthenticationPrincipal;
import cn.jiebaba.summer.security.annotation.PreAuthorize;
import cn.jiebaba.summer.security.userdetails.UserDetails;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 多链分流演练端点：{@code /api/**} 受保护，{@code /public}、{@code /info} 走兜底放行链。 */
@RestController
public class MultiChainController {

    /** 走兜底放行链：无需认证即可访问。 */
    @GetMapping("/public/hello")
    public Map<String, String> publicHello() {
        return Map.of("message", "open");
    }

    /** 走兜底放行链：非 {@code /api} 路径，验证未被 {@code /api} 链拦截。 */
    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of("area", "public", "chain", "fallback");
    }

    /** 走 {@code /api} 受保护链：需认证，注入当前主体。 */
    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", principal.getUsername());
        return body;
    }

    /** 走 {@code /api} 受保护链且要求 ADMIN 角色（方法级授权）。 */
    @GetMapping("/api/admin")
    @PreAuthorize(roles = {"ADMIN"})
    public Map<String, Object> admin(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("area", "admin");
        body.put("user", principal.getUsername());
        return body;
    }
}
