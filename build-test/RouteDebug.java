import io.summer.boot.SummerApplication;
import io.summer.web.routing.Router;
import io.summer.web.routing.RouteMapping;

public class RouteDebug {
    public static void main(String[] args) throws Exception {
        SummerApplication app = SummerApplication.run(io.summer.sample.Application.class, args);
        // 获取 router 打印所有路由
        var context = app.context();
        // 通过反射获取已注册的路由 — Router 不在 context 里，但我们可以从 WebRouteRegistrar 重新构建
        // 直接从 server 的 router 字段读取
        var serverField = app.getClass().getDeclaredField("webServer");
        serverField.setAccessible(true);
        var server = serverField.get(app);
        // server doesn't expose router directly, let's just print the routes from context beans
        // Actually let's just stop and print from the SummerWebServer
        // The server has a router field but it's private
        var routerField = server.getClass().getDeclaredField("router");
        routerField.setAccessible(true);
        Router router = (Router) routerField.get(server);
        System.out.println("=== Registered routes ===");
        for (RouteMapping r : router.routes()) {
            System.out.println("  " + r.httpMethod() + " " + r.pattern().pattern() + " -> " + r.handlerMethod().getName());
        }
        System.out.println("Total: " + router.routes().size());
        app.webServer().stop();
        app.context().close();
    }
}