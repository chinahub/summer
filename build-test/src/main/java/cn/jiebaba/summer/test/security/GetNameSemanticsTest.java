package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.GrantedAuthority;
import cn.jiebaba.summer.security.core.UsernamePasswordAuthenticationToken;
import cn.jiebaba.summer.security.userdetails.UserDetails;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * SEC-1 回归测试：{@code Authentication.getName()} 对 {@link UserDetails} 主体
 * 必须返回 {@code getUsername()}，而非 {@code principal.toString()}。
 * JWT 登录签发（JwtLoginFilter 以 getName() 作为 sub）依赖此语义——
 * 实现类不重写 {@code toString()} 时，旧实现会把 {@code Xxx@hash} 写入 sub，
 * 导致后续请求 {@code @AuthenticationPrincipal} 重载失败返回 401。
 */
public class GetNameSemanticsTest {

    /** 匿名 UserDetails 实现：刻意不重写 {@code toString()}，复现 SEC-1 原始故障条件。 */
    private static class AnonymousUser implements UserDetails {
        @Override public String getUsername() { return "alice"; }
        @Override public String getPassword() { return "secret"; }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    }

    @Test
    void authenticatedTokenWithUserDetailsPrincipalReturnsUsername() {
        var token = new UsernamePasswordAuthenticationToken(new AnonymousUser(), null, List.of());
        Assertions.assertEquals("alice", token.getName(),
                "getName() must return getUsername() for UserDetails principal");
    }

    @Test
    void unauthenticatedTokenWithStringPrincipalUnchanged() {
        var token = new UsernamePasswordAuthenticationToken("alice", "pw");
        Assertions.assertEquals("alice", token.getName());
    }

    @Test
    void nullPrincipalReturnsEmptyString() {
        var token = new UsernamePasswordAuthenticationToken(null, null);
        Assertions.assertEquals("", token.getName());
    }

    @Test
    void defaultAuthenticationImplementationReturnsUsername() {
        Authentication auth = new Authentication() {
            @Override public Object getPrincipal() { return new AnonymousUser(); }
            @Override public Object getCredentials() { return null; }
            @Override public Collection<? extends GrantedAuthority> getAuthorities() { return Collections.emptyList(); }
            @Override public Object getDetails() { return null; }
            @Override public boolean isAuthenticated() { return true; }
            @Override public void setAuthenticated(boolean authenticated) { }
        };
        Assertions.assertEquals("alice", auth.getName(),
                "Authentication default getName() must prefer getUsername() for UserDetails principal");
    }
}
