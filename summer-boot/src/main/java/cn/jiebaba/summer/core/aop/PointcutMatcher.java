package cn.jiebaba.summer.core.aop;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 {@code execution(...)} 切点表达式与目标方法进行匹配。
 * 支持一个实用子集：execution(retType pkg..Type.method(params))，可用通配符 {@code *}
 * 、以 {@code ..} 表示的包前缀，以及 {@code (..)} 形参。
 *
 * 示例：
 *   execution(* com.example.service..*.*(..))
 *   execution(public * com.example.UserService.findById(..))
 *   execution(* save*(..))
 */
public final class PointcutMatcher {

    private static final Pattern EXEC =
            Pattern.compile("execution\\s*\\(\\s*(.*?)\\s*\\)");

    /** 当表达式匹配给定方法时返回 true。 */
    public static boolean matches(String expression, Class<?> targetClass, Method method) {
        if (expression == null || expression.isBlank()) return false;
        Matcher m = EXEC.matcher(expression.trim());
        String body = m.find() ? m.group(1).trim() : expression.trim();
        return matchesBody(body, targetClass, method);
    }

    /**
     * 判断方法是否匹配切点表达式主体（返回类型、限定名与参数），支持通配符匹配。
     */
    private static boolean matchesBody(String body, Class<?> targetClass, Method method) {
        // 在最后一个 '(' 处拆分为 "retType qualifiedName(params)"
        int paren = body.lastIndexOf('(');
        if (paren < 0) return false;
        String head = body.substring(0, paren).trim();
        String params = body.substring(paren + 1).replace(")", "").trim();

        // head = retType + qualifiedName；从左侧拆分，retType 为首个 token
        String[] tokens = head.split("\\s+");
        if (tokens.length < 2) return false;
        String retType = tokens[0];
        StringBuilder namePart = new StringBuilder();
        for (int i = 1; i < tokens.length; i++) {
            if (i > 1) namePart.append(' ');
            namePart.append(tokens[i]);
        }
        String qualifiedName = namePart.toString().trim();

        if (!"*".equals(retType) && !retType.equals(method.getReturnType().getSimpleName())
                && !retType.equals(method.getReturnType().getName())) {
            return false;
        }

        String methodFqn = targetClass.getName() + "." + method.getName();
        String methodShort = targetClass.getSimpleName() + "." + method.getName();
        String pkg = targetClass.getPackageName();

        if (!matchName(qualifiedName, methodFqn, methodShort, pkg, targetClass, method)) {
            return false;
        }
        return matchParams(params, method);
    }

    private static boolean matchName(String pattern, String methodFqn, String methodShort,
                                    String pkg, Class<?> targetClass, Method method) {
        // 将切点名模式转为正则：* -> [^.]*? ，.. -> .*
        // 按 '#' 标记拆分以做两种通配符转换。
        java.util.regex.Pattern regex = patternToRegex(pattern);
        // 同时匹配全限定 "pkg.Type.method" 与 "Type.method"
        return regex.matcher(methodFqn).matches()
                || regex.matcher(methodShort).matches()
                || regex.matcher(pkg + "." + targetClass.getSimpleName() + "." + method.getName()).matches();
    }

    private static java.util.regex.Pattern patternToRegex(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '.' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '.') {
                sb.append(".*");
                i++;
            } else if (c == '*') {
                sb.append("[^.]*");
            } else if ("[]{}(),|^$\\?+".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else if (c == '.') {
                sb.append("\\.");
            } else {
                sb.append(c);
            }
        }
        return java.util.regex.Pattern.compile(sb.toString());
    }

    private static boolean matchParams(String params, Method method) {
        if ("..".equals(params)) return true;
        Class<?>[] paramTypes = method.getParameterTypes();
        if ("*".equals(params) || "".equals(params)) {
            return paramTypes.length == 0;
        }
        String[] declared = params.split("\\s*,\\s*");
        if (declared.length != paramTypes.length) return false;
        for (int i = 0; i < declared.length; i++) {
            String d = declared[i].trim();
            if ("*".equals(d)) continue;
            String simple = paramTypes[i].getSimpleName();
            String fqn = paramTypes[i].getName();
            if (!d.equals(simple) && !d.equals(fqn)) return false;
        }
        return true;
    }
}
