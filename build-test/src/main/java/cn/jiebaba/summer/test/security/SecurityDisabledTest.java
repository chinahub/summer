package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.security.web.HttpSecurity;
import cn.jiebaba.summer.security.web.MethodSecurityEnforcer;
import cn.jiebaba.summer.security.web.SecurityFilterChain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/** 验证 opt-in/禁用行为：安全关闭时必须为空操作。 */
public class SecurityDisabledTest {

    @Test
    public void disabledEnforcerIsNoOp() throws Exception {
        MethodSecurityEnforcer enforcer = new MethodSecurityEnforcer(false);
        // 任意方法，上下文无认证信息 -> 不得抛异常（空操作）
        Method m = String.class.getMethod("length");
        enforcer.check(m);
        Assertions.assertTrue(!enforcer.isEnabled(), "disabled enforcer reports disabled");
    }

    @Test
    public void httpSecurityWithoutJwtBuildsEmptyChain() {
        SecurityFilterChain chain = HttpSecurity.security()
                .authorize(HttpSecurity.anyRequest().permitAll())
                .build();
        Assertions.assertTrue(!chain.isEnabled(), "chain without JWT config is disabled (empty)");
        Assertions.assertTrue(chain.filters().isEmpty(), "no filters when JWT not configured");
    }

    @Test
    public void enabledEnforcerRequiresAuth() {
        MethodSecurityEnforcer enforcer = new MethodSecurityEnforcer(true);
        Method m;
        try {
            m = SecurityDisabledTest.class.getDeclaredMethod("protectedMethod");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        boolean threw = false;
        try {
            enforcer.check(m);
        } catch (RuntimeException e) {
            threw = true; // 预期抛出 AuthenticationException（401），上下文无认证信息
        }
        Assertions.assertTrue(threw, "enabled enforcer on @PreAuthorize method with no auth must throw");
    }

    @cn.jiebaba.summer.security.annotation.PreAuthorize(roles = {"ADMIN"})
    private void protectedMethod() {}
}
