package cn.jiebaba.summer.security.web;

/**
 * Minimal Ant-style path matcher supporting {@code ?}, {@code *}, and {@code **}.
 * Sufficient for URL authorization rules like {@code /public/**}, {@code /api/*}, {@code /admin/**}.
 */
final class AntPathMatcher {

    private AntPathMatcher() {}

    static boolean match(String pattern, String path) {
        if (pattern == null || path == null) return false;
        // normalize: strip trailing slash except root
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (pattern.length() > 1 && pattern.endsWith("/")) pattern = pattern.substring(0, pattern.length() - 1);
        return doMatch(pattern, 0, path, 0);
    }

    private static boolean doMatch(String pattern, int pStart, String path, int sStart) {
        int pLen = pattern.length();
        int sLen = path.length();
        while (pStart < pLen) {
            // consume a single '*' (not '**')
            if (pStart < pLen - 1 && pattern.charAt(pStart) == '*' && pattern.charAt(pStart + 1) == '*') {
                pStart += 2;
                // skip an optional following '/'
                if (pStart < pLen && pattern.charAt(pStart) == '/') pStart++;
                // '**' matches zero or more path segments; try every position
                for (int k = sStart; k <= sLen; k++) {
                    if (doMatch(pattern, pStart, path, k)) return true;
                    // stop at segment boundaries only; but try all anyway
                    if (k < sLen && path.charAt(k) != '/' ) {
                        // keep scanning; only '/' or end are valid split points for /**/
                    }
                }
                // also handle pattern ending in '**' matching the entire rest
                return pStart >= pLen;
            }
            if (pStart < pLen && pattern.charAt(pStart) == '*') {
                pStart++;
                // '*' matches zero or more chars except '/'
                int nextSep = indexOfSlash(pattern, pStart);
                String literal = pattern.substring(pStart, nextSep);
                int searchFrom = sStart;
                while (true) {
                    int found = path.indexOf(literal, searchFrom);
                    if (found < 0) return false;
                    // ensure nothing but non-slash chars were matched between sStart and found
                    if (!containsSlash(path, sStart, found)) {
                        if (doMatch(pattern, nextSep, path, found + literal.length())) return true;
                    }
                    searchFrom = found + 1;
                }
            }
            // literal segment (possibly with '?')
            int nextP = nextWildcardOrEnd(pattern, pStart);
            String token = pattern.substring(pStart, nextP);
            if (!regionMatchesWithQ(pattern, pStart, nextP, path, sStart)) {
                // no match for literal token
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
