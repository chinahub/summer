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
 * Enforces method-level {@link PreAuthorize} / {@link PermitAll} / {@link DenyAll}
 * on a web handler method, against the current {@link SecurityContextHolder}.
 * Method annotations take precedence over type-level annotations.
 * <p>Implements the {@code summer-web} {@link HandlerMethodAccessChecker} SPI so the
 * dispatcher can invoke it after route matching without depending on the security
 * module. Throws {@link ResponseStatusException} (401/403) so the generic web
 * dispatcher can translate the failure to an HTTP response without any dependency
 * on security exception types. When {@code enabled} is false, it is a no-op so
 * existing applications are unaffected.
 */
public final class MethodSecurityEnforcer implements HandlerMethodAccessChecker {

    private final boolean enabled;

    public MethodSecurityEnforcer(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
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
        if (preAuthorize == null) return; // no method-level security

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
