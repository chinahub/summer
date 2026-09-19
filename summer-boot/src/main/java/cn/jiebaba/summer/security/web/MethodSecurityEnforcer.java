package cn.jiebaba.summer.security.web;

import cn.jiebaba.summer.security.annotation.DenyAll;
import cn.jiebaba.summer.security.annotation.PermitAll;
import cn.jiebaba.summer.security.annotation.PreAuthorize;
import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.GrantedAuthority;
import cn.jiebaba.summer.security.core.SecurityContextHolder;
import cn.jiebaba.summer.security.core.SimpleGrantedAuthority;
import cn.jiebaba.summer.web.bind.HandlerMethodAccessChecker;
import cn.jiebaba.summer.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 依据当前 {@link SecurityContextHolder}，对 Web 处理器方法强制方法级
 * {@link PreAuthorize} / {@link PermitAll} / {@link DenyAll}。方法注解优先于类型级注解。
 * <p>实现 {@code summer-web} 的 {@link HandlerMethodAccessChecker} SPI，使调度器
 * 在路由匹配后调用它而无需依赖安全模块。抛出 {@link ResponseStatusException}（401/403），
 * 使通用 Web 调度器无需依赖安全异常类型即可将失败转为 HTTP 响应。
 * 当 {@code enabled} 为 false 时为空操作，对现有应用无影响。
 */
public final class MethodSecurityEnforcer implements HandlerMethodAccessChecker {

    private final boolean enabled;

    public MethodSecurityEnforcer(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    /**
     * 检查处理器方法上的方法级安全注解：按 @PreAuthorize/@PermitAll/@DenyAll
     * 校验当前主体权限，不满足时抛 401/403。
     */
    public void check(Method handlerMethod) {
        if (!enabled || handlerMethod == null) return;

        if (handlerMethod.isAnnotationPresent(PermitAll.class)) return;
        if (handlerMethod.isAnnotationPresent(DenyAll.class)) {
            throw new ResponseStatusException(403, "Access denied by @DenyAll");
        }
        PreAuthorize preAuthorize = handlerMethod.getAnnotation(PreAuthorize.class);
        if (preAuthorize == null) {
            preAuthorize = handlerMethod.getDeclaringClass().getAnnotation(PreAuthorize.class);
        }
        if (preAuthorize == null) return; // 无方法级安全注解

        Authentication auth = SecurityContextHolder.getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(401, "Authentication required");
        }
        if (!hasAccess(auth, preAuthorize)) {
            throw new ResponseStatusException(403, "Insufficient authority for " + handlerMethod.getName());
        }
    }

    public boolean isEnabled() { return enabled; }

    private boolean hasAccess(Authentication auth, PreAuthorize preAuthorize) {
        String[] roles = preAuthorize.roles();
        String[] authorities = preAuthorize.authorities();
        boolean requireAll = preAuthorize.requireAll();

        boolean hasRoles = roles.length > 0;
        boolean hasAuthorities = authorities.length > 0;
        if (!hasRoles && !hasAuthorities) return true;

        Collection<? extends GrantedAuthority> owned = auth.getAuthorities();
        Set<String> ownedSet = owned.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        boolean rolesOk = !hasRoles || checkNames(ownedSet, roles, requireAll, true);
        boolean authOk = !hasAuthorities || checkNames(ownedSet, authorities, requireAll, false);
        return rolesOk && authOk;
    }

    private boolean checkNames(Set<String> owned, String[] names, boolean requireAll, boolean asRole) {
        if (requireAll) {
            for (String n : names) {
                String want = asRole ? roleAuthority(n) : n;
                if (!owned.contains(want)) return false;
            }
            return true;
        }
        for (String n : names) {
            String want = asRole ? roleAuthority(n) : n;
            if (owned.contains(want)) return true;
        }
        return false;
    }

    private static String roleAuthority(String role) {
        return role.startsWith(SimpleGrantedAuthority.ROLE_PREFIX) ? role
                : SimpleGrantedAuthority.ROLE_PREFIX + role;
    }
}
