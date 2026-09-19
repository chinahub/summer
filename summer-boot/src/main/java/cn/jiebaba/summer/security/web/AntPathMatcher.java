package cn.jiebaba.summer.security.web;

/**
 * 极简的 Ant 风格路径匹配器，支持 {@code ?}、{@code *} 与 {@code **}。
 * 足以处理 URL 授权规则，如 {@code /public/**}、{@code /api/*}、{@code /admin/**}。
 */
final class AntPathMatcher {

    private AntPathMatcher() {}

    static boolean match(String pattern, String path) {
        if (pattern == null || path == null) return false;
        // 规范化：去除尾部斜杠（根除外）
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (pattern.length() > 1 && pattern.endsWith("/")) pattern = pattern.substring(0, pattern.length() - 1);
        return doMatch(pattern, 0, path, 0);
    }

    /**
     * 递归匹配 Ant 风格模式与路径：支持 {@code ?}、{@code *}（单段）与 {@code **}（跨段）。
     */
    private static boolean doMatch(String pattern, int pStart, String path, int sStart) {
        int pLen = pattern.length();
        int sLen = path.length();
        while (pStart < pLen) {
            // 消费单个 '*'（非 '**'）
            if (pStart < pLen - 1 && pattern.charAt(pStart) == '*' && pattern.charAt(pStart + 1) == '*') {
                pStart += 2;
                // 跳过可选的紧随 '/'
                if (pStart < pLen && pattern.charAt(pStart) == '/') pStart++;
                // '**' 匹配零或多个路径段；尝试每个位置
                for (int k = sStart; k <= sLen; k++) {
                    if (doMatch(pattern, pStart, path, k)) return true;
                    // 仅在段边界停止；但仍逐一尝试
                    if (k < sLen && path.charAt(k) != '/' ) {
                        // 继续扫描；仅 '/' 或末尾是 /**/ 的有效分割点
                    }
                }
                // 同时处理以 '**' 结尾的模式匹配剩余全部
                return pStart >= pLen;
            }
            if (pStart < pLen && pattern.charAt(pStart) == '*') {
                pStart++;
                // '*' 匹配零或多个非 '/' 字符
                int nextSep = indexOfSlash(pattern, pStart);
                String literal = pattern.substring(pStart, nextSep);
                int searchFrom = sStart;
                while (true) {
                    int found = path.indexOf(literal, searchFrom);
                    if (found < 0) return false;
                    // 确保 sStart 与 found 之间仅匹配了非斜杠字符
                    if (!containsSlash(path, sStart, found)) {
                        if (doMatch(pattern, nextSep, path, found + literal.length())) return true;
                    }
                    searchFrom = found + 1;
                }
            }
            // 字面段（可能含 '?'）
            int nextP = nextWildcardOrEnd(pattern, pStart);
            String token = pattern.substring(pStart, nextP);
            if (!regionMatchesWithQ(pattern, pStart, nextP, path, sStart)) {
                // 字面 token 不匹配
                return false;
            }
            sStart += token.length();
            pStart = nextP;
        }
        return sStart == sLen;
    }

    private static int indexOfSlash(String s, int from) {
        int idx = s.indexOf('/', from);
        return idx < 0 ? s.length() : idx;
    }

    private static boolean containsSlash(String s, int from, int to) {
        for (int i = from; i < to; i++) if (s.charAt(i) == '/') return true;
        return false;
    }

    private static int nextWildcardOrEnd(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '*' || c == '?') return i;
        }
        return s.length();
    }

    private static boolean regionMatchesWithQ(String pattern, int pFrom, int pTo, String path, int sFrom) {
        int len = pTo - pFrom;
        if (sFrom + len > path.length()) return false;
        for (int i = 0; i < len; i++) {
            char pc = pattern.charAt(pFrom + i);
            char sc = path.charAt(sFrom + i);
            if (pc == '?') continue;
            if (pc != sc) return false;
        }
        return true;
    }
}
