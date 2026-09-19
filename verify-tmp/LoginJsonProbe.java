import cn.jiebaba.summer.security.jwt.JsonReader;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 验证 issue 1：/login 请求体解析对 BOM / 非 JSON 输入的行为。
 * 覆盖 JsonReader 剥 BOM、错误信息含实际字符，以及 LoginBodyParser 的异常传递
 * （JwtLoginFilter 捕获 IllegalArgumentException → 400 已由代码审查确认）。
 */
public class LoginJsonProbe {
    public static void main(String[] args) throws Exception {
        // 1. 合法 JSON + UTF-8 BOM → 应解析成功
        Map<String, Object> bom = JsonReader.read("\uFEFF{\"username\":\"admin\",\"password\":\"admin123\"}");
        check("admin".equals(bom.get("username")), "BOM JSON 解析");

        // 2. 纯文本 abc → 应抛 IllegalArgumentException 且含实际字符
        try {
            JsonReader.read("abc");
            check(false, "纯文本应抛异常");
        } catch (IllegalArgumentException e) {
            check(e.getMessage() != null && e.getMessage().contains("got 'a'"),
                    "错误信息含实际字符: " + e.getMessage());
        }

        // 3. LoginBodyParser（包私有，反射调用）：空串→空 Map；abc→IAE（由 JwtLoginFilter 转 400）
        Class<?> parser = Class.forName("cn.jiebaba.summer.security.web.LoginBodyParser");
        Method parse = parser.getDeclaredMethod("parse", String.class);
        parse.setAccessible(true);
        Map<?, ?> empty = (Map<?, ?>) parse.invoke(null, "   ");
        check(empty.isEmpty(), "空 body 返回空 Map");
        try {
            parse.invoke(null, "\uFEFFabc");
            check(false, "BOM+纯文本应抛异常");
        } catch (java.lang.reflect.InvocationTargetException e) {
            check(e.getCause() instanceof IllegalArgumentException,
                    "LoginBodyParser 抛出 IAE（JwtLoginFilter 捕获转 400）: " + e.getCause().getMessage());
        }
        System.out.println("ALL PASS");
    }

    private static void check(boolean ok, String name) {
        if (!ok) { System.out.println("FAIL: " + name); System.exit(1); }
        System.out.println("PASS: " + name);
    }
}
