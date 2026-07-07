package cn.jiebaba.summer.web.routing;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.web.http.HttpMethod;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 断言前缀树匹配与线性参考实现在方法×路径矩阵上完全等价。 */
public class RouterTrieTest {

    private static RouteMapping route(HttpMethod method, String pattern) {
        return new RouteMapping(method, new RoutePattern(pattern), new Object(), null, new String[0]);
    }

    private static void assertSame(Router router, HttpMethod method, String path) {
        Optional<RouteMatch> trie = router.match(method, path);
        Optional<RouteMatch> linear = router.matchLinear(method, path);
        if (trie.isPresent() != linear.isPresent()) {
            Assert.fail("presence mismatch " + method + " " + path + ": trie=" + trie + " linear=" + linear);
        }
        if (trie.isEmpty()) return;
        if (trie.get().mapping() != linear.get().mapping()) {
            Assert.fail("route mismatch " + method + " " + path + ": trie=" + trie.get().mapping()
                    + " linear=" + linear.get().mapping());
        }
        if (!Objects.equals(trie.get().pathVariables(), linear.get().pathVariables())) {
            Assert.fail("vars mismatch " + method + " " + path + ": trie=" + trie.get().pathVariables()
                    + " linear=" + linear.get().pathVariables());
        }
    }

    /**
     * 构造覆盖字面量、参数、通配符与 catch-all 的路由集合，对全部 HTTP 方法与一批
     * 路径的组合断言前缀树匹配与线性参考实现完全一致。
     */
    @Test
    void trieMatchesLinearAcrossMatrix() {
        Router router = new Router();
        List.of(
                route(HttpMethod.GET, "/"),
                route(HttpMethod.GET, "/users"),
                route(HttpMethod.GET, "/users/me"),
                route(HttpMethod.GET, "/users/greeting"),
                route(HttpMethod.GET, "/users/{id}"),
                route(HttpMethod.GET, "/users/{id}/repos"),
                route(HttpMethod.GET, "/users/{id}/settings"),
                route(HttpMethod.GET, "/users/*"),
                route(HttpMethod.GET, "/admin/**"),
                route(HttpMethod.GET, "/files/{type}/{name}"),
                route(HttpMethod.GET, "/products/{id}"),
                route(HttpMethod.GET, "/products/latest"),
                route(HttpMethod.POST, "/users"),
                route(HttpMethod.POST, "/users/{id}"),
                route(HttpMethod.DELETE, "/users/{id}"),
                route(HttpMethod.GET, "/**")
        ).forEach(router::register);
        router.sortBySpecificity();

        String[] paths = {
                "/", "/users", "/users/me", "/users/greeting", "/users/42", "/users/42/repos",
                "/users/42/settings", "/users/42/extra", "/users/", "//users", "/users/42/",
                "/admin", "/admin/x", "/admin/x/y/z", "/files/img/logo", "/files/a/b/c",
                "/products/latest", "/products/5", "/products", "/products/", "/nope", "/anything/here"
        };
        for (HttpMethod method : HttpMethod.values()) {
            for (String path : paths) {
                assertSame(router, method, path);
            }
        }
    }

    @Test
    void emptyRouterMatchesNothing() {
        Router router = new Router();
        router.sortBySpecificity();
        Assert.assertTrue(router.match(HttpMethod.GET, "/x").isEmpty(), "empty router should not match");
    }

    @Test
    void differentMethodsAreIsolated() {
        Router router = new Router();
        RouteMapping post = route(HttpMethod.POST, "/x");
        router.register(post);
        router.sortBySpecificity();
        Assert.assertTrue(router.match(HttpMethod.GET, "/x").isEmpty(), "GET should not match POST route");
        Optional<RouteMatch> m = router.match(HttpMethod.POST, "/x");
        Assert.assertTrue(m.isPresent(), "POST should match");
        Assert.assertTrue(m.get().mapping() == post, "should return the POST route");
    }
}
