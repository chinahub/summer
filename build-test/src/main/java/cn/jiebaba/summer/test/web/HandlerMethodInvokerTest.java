package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.core.context.DefaultApplicationContext;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.web.bind.HandlerException;
import cn.jiebaba.summer.web.bind.HandlerMethodInvoker;
import cn.jiebaba.summer.web.convert.JsonMessageConverter;
import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.RawHttpRequest;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.routing.RouteMapping;
import cn.jiebaba.summer.web.routing.RouteMatch;
import cn.jiebaba.summer.web.routing.RoutePattern;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Set;

/** 验证 HandlerMethodInvoker 的参数绑定与 MethodHandle 折叠调用路径。 */
public class HandlerMethodInvokerTest {

    private DefaultApplicationContext ctx;
    private HandlerMethodInvoker invoker;
    private SampleController bean;

    private void setup() {
        ctx = new DefaultApplicationContext(null, null, Set.of("cn.jiebaba.summer.test.web"));
        ctx.refresh();
        invoker = new HandlerMethodInvoker(ctx, new JsonMessageConverter());
        bean = ctx.getBean(SampleController.class);
    }

    private void teardown() {
        if (ctx != null) ctx.close();
    }

    private WebRequest request(String raw) throws Exception {
        return new WebRequest(RawHttpRequest.parse(
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), 8192, 8388608));
    }

    private WebRequest post(String body) throws Exception {
        String raw = "POST /x HTTP/1.1\r\nHost: x\r\nContent-Length: " + body.length() + "\r\n\r\n" + body;
        return request(raw);
    }

    private RouteMatch match(String name, Class<?>... params) throws Exception {
        Method m = SampleController.class.getMethod(name, params);
        return new RouteMatch(new RouteMapping(HttpMethod.GET, new RoutePattern("/x"), bean, m, new String[0]),
                new LinkedHashMap<>());
    }

    private WebResponse response() {
        return new WebResponse(java.nio.channels.Channels.newChannel(new ByteArrayOutputStream()));
    }

    @Test
    void pathVarLong() throws Exception {
        setup();
        try {
            RouteMatch rm = match("get", Long.class);
            rm.pathVariables().put("id", "42");
            Assert.assertEquals("id=42", invoker.invoke(rm, request("GET /x HTTP/1.1\r\nHost: x\r\n\r\n"), response()));
            assertSpreaderBuilt("get", Long.class);
        } finally {
            teardown();
        }
    }

    @Test
    void requestBodyRecord() throws Exception {
        setup();
        try {
            RouteMatch rm = match("create", User.class);
            Assert.assertEquals("name=alice,age=30", invoker.invoke(rm, post("{\"name\":\"alice\",\"age\":30}"), response()));
        } finally {
            teardown();
        }
    }

    @Test
    void pathVarAndBody() throws Exception {
        setup();
        try {
            RouteMatch rm = match("update", Long.class, User.class);
            rm.pathVariables().put("id", "9");
            Assert.assertEquals("id=9:bob", invoker.invoke(rm, post("{\"name\":\"bob\",\"age\":1}"), response()));
        } finally {
            teardown();
        }
    }

    @Test
    void requestParamWithDefault() throws Exception {
        setup();
        try {
            RouteMatch rm = match("search", String.class, int.class);
            Assert.assertEquals("alice:5", invoker.invoke(rm, request("GET /x?name=alice&limit=5 HTTP/1.1\r\nHost: x\r\n\r\n"), response()));
            Assert.assertEquals("alice:10", invoker.invoke(rm, request("GET /x?name=alice HTTP/1.1\r\nHost: x\r\n\r\n"), response()));
        } finally {
            teardown();
        }
    }

    @Test
    void requestHeader() throws Exception {
        setup();
        try {
            RouteMatch rm = match("header", String.class);
            Assert.assertEquals("trace=abc", invoker.invoke(rm, request("GET /x HTTP/1.1\r\nHost: x\r\nX-Trace: abc\r\n\r\n"), response()));
        } finally {
            teardown();
        }
    }

    @Test
    void noArgsAndVoid() throws Exception {
        setup();
        try {
            Assert.assertEquals("ok", invoker.invoke(match("noargs"), request("GET /x HTTP/1.1\r\nHost: x\r\n\r\n"), response()));
            Assert.assertNull(invoker.invoke(match("fire"), request("GET /x HTTP/1.1\r\nHost: x\r\n\r\n"), response()));
        } finally {
            teardown();
        }
    }

    @Test
    void primitiveReturnAndParam() throws Exception {
        setup();
        try {
            Assert.assertEquals(Integer.valueOf(7), invoker.invoke(match("count"), request("GET /x HTTP/1.1\r\nHost: x\r\n\r\n"), response()));
            Assert.assertEquals("n=5", invoker.invoke(match("echo", int.class), request("GET /x?n=5 HTTP/1.1\r\nHost: x\r\n\r\n"), response()));
        } finally {
            teardown();
        }
    }

    @Test
    void recordReturn() throws Exception {
        setup();
        try {
            Assert.assertEquals(new User(7L, "x", 9), invoker.invoke(match("recordReturn"), request("GET /x HTTP/1.1\r\nHost: x\r\n\r\n"), response()));
        } finally {
            teardown();
        }
    }

    @Test
    void checkedExceptionWrappedAsHandlerException() throws Exception {
        setup();
        try {
            RouteMatch rm = match("boom");
            HandlerException he = Assert.assertThrows(HandlerException.class,
                    () -> invoker.invoke(rm, request("GET /x HTTP/1.1\r\nHost: x\r\n\r\n"), response()));
            Assert.assertTrue(he.getCause() != null && he.getCause().getMessage().contains("boom"),
                    "cause should carry original message");
        } finally {
            teardown();
        }
    }

    /** 反射断言折叠调用句柄已构建（即走 MethodHandle 而非反射回退）。 */
    private void assertSpreaderBuilt(String name, Class<?>... params) throws Exception {
        Method m = SampleController.class.getMethod(name, params);
        java.lang.reflect.Field cf = HandlerMethodInvoker.class.getDeclaredField("cache");
        cf.setAccessible(true);
        Object cache = cf.get(invoker);
        Object cached = cache.getClass().getMethod("get", Object.class).invoke(cache, m);
        Assert.assertNotNull(cached, "CachedMethod should exist for " + name);
        java.lang.reflect.Field sf = cached.getClass().getDeclaredField("spreader");
        sf.setAccessible(true);
        Assert.assertNotNull(sf.get(cached), "MethodHandle spreader should be built (not fallback) for " + name);
    }
}
