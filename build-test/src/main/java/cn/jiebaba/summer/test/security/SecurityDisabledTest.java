package cn.jiebaba.summer.test.security;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.security.web.HttpSecurity;
import cn.jiebaba.summer.security.web.MethodSecurityEnforcer;
import cn.jiebaba.summer.security.web.SecurityFilterChain;

import java.lang.reflect.Method;

/** Verifies the opt-in/disable behavior: security off must be inert. */
public class SecurityDisabledTest {

    @Test
    public void disabledEnforcerIsNoOp() throws Exception {
        MethodSecurityEnforcer enforcer = new MethodSecurityEnforcer(false);
        // any method, no auth in context -> must NOT throw (no-op)
        Method m = String.class.getMethod("length");
        enforcer.check(m);
        Assert.assertTrue(!enforcer.isEnabled(), "disabled enforcer reports disabled");
    }

    @Test
    public void httpSecurityWithoutJwtBuildsEmptyChain() {
        SecurityFilterChain chain = HttpSecurity.security()
                .authorize(HttpSecurity.anyRequest().permitAll())
                .build();
        Assert.assertTrue(!chain.isEnabled(), "chain without JWT config is disabled (empty)");
        Assert.assertTrue(chain.filters().isEmpty(), "no filters when JWT not configured");
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
            threw = true; // AuthenticationException (401) expected, no auth in context
        }
        Assert.assertTrue(threw, "enabled enforcer on @PreAuthorize method with no auth must throw");
    }

    @cn.jiebaba.summer.security.annotation.PreAuthorize(roles = {"ADMIN"})
    private void protectedMethod() {}
}
