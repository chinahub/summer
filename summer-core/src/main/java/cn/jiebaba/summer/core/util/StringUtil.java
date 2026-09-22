package cn.jiebaba.summer.core.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 字符串工具，灵感来自 {@code org.apache.commons.lang3.StringUtils}。
 *
 * <p>所有方法均容忍 null：对于 "contains"/"index" 类查询，{@code null} 输入按空串处理；
 * 对于转换类查询则传播为 {@code null}/空串，与 commons-lang3 的约定一致。
 */
public final class StringUtil {

    private StringUtil() {}

    public static final String EMPTY = "";
    public static final String SPACE = " ";
    public static final String LF = "\n";
    public static final String CR = "\r";
    public static final int INDEX_NOT_FOUND = -1;

    public static boolean isEmpty(CharSequence cs) { return cs == null || cs.isEmpty(); }
    public static boolean isNotEmpty(CharSequence cs) { return !isEmpty(cs); }

    public static boolean isBlank(CharSequence cs) {
        if (cs == null) return true;
        for (int i = 0; i < cs.length(); i++) {
            if (!Character.isWhitespace(cs.charAt(i))) return false;
        }
        return true;
    }
    public static boolean isNotBlank(CharSequence cs) { return !isBlank(cs); }

    public static String trim(String str) { return str == null ? null : str.trim(); }
    public static String trimToNull(String str) { String t = trim(str); return isEmpty(t) ? null : t; }
    public static String trimToEmpty(String str) { return str == null ? EMPTY : str.trim(); }
    public static String strip(String str) { return str == null ? null : str.strip(); }
    public static String stripToNull(String str) { String s = strip(str); return isEmpty(s) ? null : s; }
    public static String stripToEmpty(String str) { return str == null ? EMPTY : str.strip(); }

    public static boolean equals(CharSequence cs1, CharSequence cs2) {
        if (cs1 == cs2) return true;
        if (cs1 == null || cs2 == null) return false;
        if (cs1.length() != cs2.length()) return false;
        return cs1.toString().equals(cs2.toString());
    }
    public static boolean equalsIgnoreCase(CharSequence cs1, CharSequence cs2) {
        if (cs1 == cs2) return true;
        if (cs1 == null || cs2 == null) return false;
        return cs1.toString().equalsIgnoreCase(cs2.toString());
    }
    public static boolean equalsAny(CharSequence cs, CharSequence... searchStrings) {
        if (searchStrings == null) return false;
        for (CharSequence candidate : searchStrings) { if (equals(cs, candidate)) return true; }
        return false;
    }
    public static int compare(String s1, String s2) { return compare(s1, s2, false); }
    public static int compare(String s1, String s2, boolean nullIsLess) {
        if (s1 == s2) return 0;
        if (s1 == null) return nullIsLess ? -1 : 1;
        if (s2 == null) return nullIsLess ? 1 : -1;
        return s1.compareTo(s2);
    }
    public static int compareIgnoreCase(String s1, String s2) { return compareIgnoreCase(s1, s2, false); }
    public static int compareIgnoreCase(String s1, String s2, boolean nullIsLess) {
        if (s1 == s2) return 0;
        if (s1 == null) return nullIsLess ? -1 : 1;
        if (s2 == null) return nullIsLess ? 1 : -1;
        return s1.compareToIgnoreCase(s2);
    }

    public static boolean startsWith(CharSequence str, CharSequence prefix) { return startsWith(str, prefix, false); }
    public static boolean startsWithIgnoreCase(CharSequence str, CharSequence prefix) { return startsWith(str, prefix, true); }
    private static boolean startsWith(CharSequence str, CharSequence prefix, boolean ignoreCase) {
        if (str == null || prefix == null) return false;
        if (prefix.length() > str.length()) return false;
        String s = str.toString(); String p = prefix.toString();
        return ignoreCase ? s.regionMatches(true, 0, p, 0, p.length()) : s.startsWith(p);
    }
    public static boolean startsWithAny(CharSequence str, CharSequence... prefixes) {
        if (str == null || prefixes == null) return false;
        for (CharSequence p : prefixes) { if (startsWith(str, p)) return true; }
        return false;
    }
    public static boolean endsWith(CharSequence str, CharSequence suffix) { return endsWith(str, suffix, false); }
    public static boolean endsWithIgnoreCase(CharSequence str, CharSequence suffix) { return endsWith(str, suffix, true); }
    private static boolean endsWith(CharSequence str, CharSequence suffix, boolean ignoreCase) {
        if (str == null || suffix == null) return false;
        if (suffix.length() > str.length()) return false;
        String s = str.toString(); String suf = suffix.toString();
        int offset = s.length() - suf.length();
        return ignoreCase ? s.regionMatches(true, offset, suf, 0, suf.length()) : s.endsWith(suf);
    }
    public static boolean endsWithAny(CharSequence str, CharSequence... suffixes) {
        if (str == null || suffixes == null) return false;
        for (CharSequence s : suffixes) { if (endsWith(str, s)) return true; }
        return false;
    }
    public static boolean contains(CharSequence seq, int searchChar) {
        return isEmpty(seq) ? false : seq.toString().indexOf(searchChar) >= 0;
    }
    public static boolean contains(CharSequence seq, CharSequence searchSeq) {
        if (seq == null || searchSeq == null) return false;
        return seq.toString().contains(searchSeq);
    }
    public static boolean containsIgnoreCase(CharSequence str, CharSequence searchStr) {
        if (str == null || searchStr == null) return false;
        return str.toString().toLowerCase().contains(searchStr.toString().toLowerCase());
    }
    public static boolean containsAny(CharSequence cs, CharSequence... searchStrings) {
        if (isEmpty(cs) || searchStrings == null) return false;
        for (CharSequence s : searchStrings) { if (contains(cs, s)) return true; }
        return false;
    }
    public static boolean containsAny(CharSequence cs, char... searchChars) {
        if (isEmpty(cs) || searchChars == null) return false;
        String s = cs.toString();
        for (char c : searchChars) { if (s.indexOf(c) >= 0) return true; }
        return false;
    }
    public static boolean containsOnly(CharSequence cs, char... valid) {
        if (cs == null || valid == null) return false;
        if (cs.length() == 0) return true;
        if (valid.length == 0) return false;
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i); boolean found = false;
            for (char v : valid) { if (v == c) { found = true; break; } }
            if (!found) return false;
        }
        return true;
    }
    public static boolean containsNone(CharSequence cs, char... invalidChars) {
        if (cs == null || invalidChars == null) return true;
        String s = cs.toString();
        for (char c : invalidChars) { if (s.indexOf(c) >= 0) return false; }
        return true;
    }
    public static boolean containsWhitespace(CharSequence seq) {
        if (isEmpty(seq)) return false;
        for (int i = 0; i < seq.length(); i++) { if (Character.isWhitespace(seq.charAt(i))) return true; }
        return false;
    }

    public static int indexOf(CharSequence seq, int searchChar) { return isEmpty(seq) ? INDEX_NOT_FOUND : seq.toString().indexOf(searchChar); }
    public static int indexOf(CharSequence seq, int searchChar, int startPos) { return isEmpty(seq) ? INDEX_NOT_FOUND : seq.toString().indexOf(searchChar, startPos); }
    public static int indexOf(CharSequence seq, CharSequence searchSeq) { return indexOf(seq, searchSeq, 0); }
    public static int indexOf(CharSequence seq, CharSequence searchSeq, int startPos) {
        if (seq == null || searchSeq == null) return INDEX_NOT_FOUND;
        return seq.toString().indexOf(searchSeq.toString(), startPos);
    }
    public static int indexOfIgnoreCase(CharSequence str, CharSequence searchStr) { return indexOfIgnoreCase(str, searchStr, 0); }
    public static int indexOfIgnoreCase(CharSequence str, CharSequence searchStr, int startPos) {
        if (str == null || searchStr == null) return INDEX_NOT_FOUND;
        String s = str.toString(); String q = searchStr.toString();
        if (q.isEmpty()) return startPos < s.length() ? startPos : Math.max(0, s.length() - 1);
        int endLimit = s.length() - q.length() + 1;
        for (int i = startPos; i < endLimit; i++) { if (s.regionMatches(true, i, q, 0, q.length())) return i; }
        return INDEX_NOT_FOUND;
    }
    public static int ordinalIndexOf(CharSequence str, CharSequence searchStr, int ordinal) {
        if (str == null || searchStr == null || ordinal <= 0) return INDEX_NOT_FOUND;
        String s = str.toString(); String q = searchStr.toString();
        if (q.isEmpty()) return INDEX_NOT_FOUND;
        int idx = INDEX_NOT_FOUND;
        for (int i = 0; i < ordinal; i++) {
            idx = s.indexOf(q, idx == INDEX_NOT_FOUND ? 0 : idx + q.length());
            if (idx == INDEX_NOT_FOUND) return INDEX_NOT_FOUND;
        }
        return idx;
    }
    public static int lastIndexOf(CharSequence seq, int searchChar) { return isEmpty(seq) ? INDEX_NOT_FOUND : seq.toString().lastIndexOf(searchChar); }
    public static int lastIndexOf(CharSequence seq, CharSequence searchSeq) {
        if (seq == null || searchSeq == null) return INDEX_NOT_FOUND;
        return seq.toString().lastIndexOf(searchSeq.toString());
    }
    public static String substring(String str, int start) {
        if (str == null) return null;
        if (start < 0) start = str.length() + start;
        if (start < 0) start = 0;
        if (start > str.length()) return EMPTY;
        return str.substring(start);
    }
    public static String substring(String str, int start, int end) {
        if (str == null) return null;
        int len = str.length();
        if (end < 0) end = len + end;
        if (start < 0) start = len + start;
        if (end > len) end = len;
        if (start > end) return EMPTY;
        if (start < 0) start = 0;
        if (end < 0) end = 0;
        return str.substring(start, end);
    }
    public static String left(String str, int len) {
        if (str == null) return null;
        if (len < 0) return EMPTY;
        return len > str.length() ? str : str.substring(0, len);
    }
    public static String right(String str, int len) {
        if (str == null) return null;
        if (len < 0) return EMPTY;
        return len > str.length() ? str : str.substring(str.length() - len);
    }
    public static String mid(String str, int pos, int len) {
        if (str == null) return null;
        int length = str.length();
        if (len < 0 || pos > length) return EMPTY;
        if (pos < 0) pos = 0;
        if (length < pos + len) len = length - pos;
        return str.substring(pos, pos + len);
    }
    public static String substringBefore(String str, String separator) {
        if (isEmpty(str) || separator == null) return str;
        if (separator.isEmpty()) return EMPTY;
        int pos = str.indexOf(separator);
        return pos == INDEX_NOT_FOUND ? str : str.substring(0, pos);
    }
    public static String substringAfter(String str, String separator) {
        if (isEmpty(str)) return str;
        if (separator == null) return EMPTY;
        int pos = str.indexOf(separator);
        return pos == INDEX_NOT_FOUND ? EMPTY : str.substring(pos + separator.length());
    }
    public static String substringBeforeLast(String str, String separator) {
        if (isEmpty(str) || isEmpty(separator)) return str;
        int pos = str.lastIndexOf(separator);
        return pos == INDEX_NOT_FOUND ? str : str.substring(0, pos);
    }
    public static String substringAfterLast(String str, String separator) {
        if (isEmpty(str)) return str;
        if (isEmpty(separator)) return EMPTY;
        int pos = str.lastIndexOf(separator);
        return pos == INDEX_NOT_FOUND || pos == str.length() - separator.length() ? EMPTY : str.substring(pos + separator.length());
    }
    public static String substringBetween(String str, String tag) { return substringBetween(str, tag, tag); }
    public static String substringBetween(String str, String open, String close) {
        if (str == null || open == null || close == null) return null;
        int start = str.indexOf(open);
        if (start == INDEX_NOT_FOUND) return null;
        int end = str.indexOf(close, start + open.length());
        return end == INDEX_NOT_FOUND ? null : str.substring(start + open.length(), end);
    }
    public static String[] substringsBetween(String str, String open, String close) {
        if (str == null || isEmpty(open) || isEmpty(close)) return null;
        List<String> list = new ArrayList<>();
        int pos = 0;
        while (pos < str.length()) {
            int start = str.indexOf(open, pos);
            if (start < 0) break;
            start += open.length();
            int end = str.indexOf(close, start);
            if (end < 0) break;
            list.add(str.substring(start, end));
            pos = end + close.length();
        }
        return list.isEmpty() ? null : list.toArray(String[]::new);
    }

    public static String[] split(String str) { return splitWorker(str, null, false); }
    public static String[] split(String str, char separatorChar) { return splitWorker(str, String.valueOf(separatorChar), false); }
    public static String[] split(String str, String separatorChars) { return splitWorker(str, separatorChars, false); }
    public static String[] splitPreserveAllTokens(String str) { return splitWorker(str, null, true); }
    public static String[] splitPreserveAllTokens(String str, String separatorChars) { return splitWorker(str, separatorChars, true); }
    /**
     * 按分隔符拆分字符串的核心实现，支持是否保留空白 token。
     */
    private static String[] splitWorker(String str, String separatorChars, boolean preserveAllTokens) {
        if (str == null) return null;
        int len = str.length();
        if (len == 0) return new String[0];
        List<String> list = new ArrayList<>();
        int i = 0; int start = 0; boolean match = false; boolean lastMatch = false;
        if (separatorChars == null) {
            while (i < len) {
                if (Character.isWhitespace(str.charAt(i))) {
                    if (match || preserveAllTokens) { list.add(str.substring(start, i)); match = false; lastMatch = true; }
                    start = ++i; continue;
                }
                lastMatch = false; match = true; i++;
            }
        } else if (separatorChars.length() == 1) {
            char sep = separatorChars.charAt(0);
            while (i < len) {
                if (str.charAt(i) == sep) {
                    if (match || preserveAllTokens) { list.add(str.substring(start, i)); match = false; lastMatch = true; }
                    start = ++i; continue;
                }
                lastMatch = false; match = true; i++;
            }
        } else {
            while (i < len) {
                if (separatorChars.indexOf(str.charAt(i)) >= 0) {
                    if (match || preserveAllTokens) { list.add(str.substring(start, i)); match = false; lastMatch = true; }
                    start = ++i; continue;
                }
                lastMatch = false; match = true; i++;
            }
        }
        if (match || (preserveAllTokens && lastMatch)) list.add(str.substring(start, i));
        return list.toArray(String[]::new);
    }

    public static String join(Object[] array, String separator) {
        if (array == null) return null;
        String sep = separator == null ? EMPTY : separator;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) { if (i > 0) sb.append(sep); if (array[i] != null) sb.append(array[i]); }
        return sb.toString();
    }
    public static String join(Iterable<?> iterable, String separator) {
        return iterable == null ? null : join(iterable.iterator(), separator);
    }
    public static String join(Iterator<?> iterator, String separator) {
        if (iterator == null) return null;
        String sep = separator == null ? EMPTY : separator;
        StringBuilder sb = new StringBuilder(); boolean first = true;
        while (iterator.hasNext()) { if (!first) sb.append(sep); Object o = iterator.next(); if (o != null) sb.append(o); first = false; }
        return sb.toString();
    }
    public static String join(Object[] array, char separator) {
        if (array == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) { if (i > 0) sb.append(separator); if (array[i] != null) sb.append(array[i]); }
        return sb.toString();
    }
    public static String repeat(String str, int repeat) {
        if (str == null) return null;
        return repeat <= 0 ? EMPTY : str.repeat(repeat);
    }
    public static String repeat(char ch, int repeat) { return repeat <= 0 ? EMPTY : String.valueOf(ch).repeat(repeat); }

    public static String leftPad(String str, int size) { return leftPad(str, size, ' '); }
    public static String leftPad(String str, int size, char padChar) {
        if (str == null) return null;
        int pads = size - str.length();
        return pads <= 0 ? str : repeat(padChar, pads).concat(str);
    }
    public static String leftPad(String str, int size, String padStr) {
        if (str == null) return null;
        if (isEmpty(padStr)) padStr = SPACE;
        int pads = size - str.length();
        if (pads <= 0) return str;
        if (padStr.length() == 1) return repeat(padStr.charAt(0), pads).concat(str);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pads; i++) sb.append(padStr.charAt(i % padStr.length()));
        return sb.toString().concat(str);
    }
    public static String rightPad(String str, int size) { return rightPad(str, size, ' '); }
    public static String rightPad(String str, int size, char padChar) {
        if (str == null) return null;
        int pads = size - str.length();
        return pads <= 0 ? str : str.concat(repeat(padChar, pads));
    }
    public static String rightPad(String str, int size, String padStr) {
        if (str == null) return null;
        if (isEmpty(padStr)) padStr = SPACE;
        int pads = size - str.length();
        if (pads <= 0) return str;
        if (padStr.length() == 1) return str.concat(repeat(padStr.charAt(0), pads));
        StringBuilder sb = new StringBuilder(str);
        for (int i = 0; i < pads; i++) sb.append(padStr.charAt(i % padStr.length()));
        return sb.toString();
    }
    public static String center(String str, int size) { return center(str, size, ' '); }
    public static String center(String str, int size, char padChar) {
        if (str == null) return null;
        int strLen = str.length();
        int pads = size - strLen;
        if (pads <= 0) return str;
        return rightPad(leftPad(str, strLen + pads / 2, padChar), size, padChar);
    }

    public static String replace(String text, String searchString, String replacement) { return replace(text, searchString, replacement, -1); }
    public static String replaceOnce(String text, String searchString, String replacement) { return replace(text, searchString, replacement, 1); }
    public static String replace(String text, String searchString, String replacement, int max) {
        if (isEmpty(text) || isEmpty(searchString) || replacement == null || max == 0) return text;
        int start = 0; int end = text.indexOf(searchString, start);
        if (end == INDEX_NOT_FOUND) return text;
        int replLength = searchString.length();
        StringBuilder sb = new StringBuilder(text.length());
        while (end != INDEX_NOT_FOUND) {
            sb.append(text, start, end).append(replacement);
            start = end + replLength;
            if (--max == 0) break;
            end = text.indexOf(searchString, start);
        }
        sb.append(text, start, text.length());
        return sb.toString();
    }
    public static String replaceEach(String text, String[] searchList, String[] replacementList) {
        if (isEmpty(text) || searchList == null || replacementList == null
                || searchList.length == 0 || searchList.length != replacementList.length) return text;
        String result = text;
        for (int i = 0; i < searchList.length; i++) {
            if (searchList[i] == null || replacementList[i] == null) continue;
            result = replace(result, searchList[i], replacementList[i]);
        }
        return result;
    }
    public static String remove(String text, String remove) { return isEmpty(text) || isEmpty(remove) ? text : text.replace(remove, EMPTY); }
    public static String remove(String text, char remove) { return isEmpty(text) ? text : text.replace(String.valueOf(remove), EMPTY); }
    public static String removeStart(String str, String remove) {
        if (isEmpty(str) || isEmpty(remove)) return str;
        return str.startsWith(remove) ? str.substring(remove.length()) : str;
    }
    public static String removeStartIgnoreCase(String str, String remove) {
        if (isEmpty(str) || isEmpty(remove)) return str;
        return startsWithIgnoreCase(str, remove) ? str.substring(remove.length()) : str;
    }
    public static String removeEnd(String str, String remove) {
        if (isEmpty(str) || isEmpty(remove)) return str;
        return str.endsWith(remove) ? str.substring(0, str.length() - remove.length()) : str;
    }
    public static String removeEndIgnoreCase(String str, String remove) {
        if (isEmpty(str) || isEmpty(remove)) return str;
        return endsWithIgnoreCase(str, remove) ? str.substring(0, str.length() - remove.length()) : str;
    }
    public static String deleteWhitespace(String str) {
        if (isEmpty(str)) return str;
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) { char c = str.charAt(i); if (!Character.isWhitespace(c)) sb.append(c); }
        return sb.toString();
    }
    public static String normalizeSpace(String str) {
        if (isEmpty(str)) return str;
        StringBuilder sb = new StringBuilder(str.length()); boolean lastWasSpace = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isWhitespace(c)) { if (!lastWasSpace) sb.append(SPACE); lastWasSpace = true; }
            else { sb.append(c); lastWasSpace = false; }
        }
        return trim(sb.toString());
    }

    public static String capitalize(String str) {
        if (isEmpty(str)) return str;
        char first = str.charAt(0);
        return Character.isTitleCase(first) ? str : Character.toTitleCase(first) + str.substring(1);
    }
    public static String uncapitalize(String str) {
        if (isEmpty(str)) return str;
        char first = str.charAt(0);
        return Character.isLowerCase(first) ? str : Character.toLowerCase(first) + str.substring(1);
    }
    public static String upperCase(String str) { return str == null ? null : str.toUpperCase(); }
    public static String lowerCase(String str) { return str == null ? null : str.toLowerCase(); }
    public static String swapCase(String str) {
        if (isEmpty(str)) return str;
        char[] chars = str.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c) || Character.isTitleCase(c)) chars[i] = Character.toLowerCase(c);
            else if (Character.isLowerCase(c)) chars[i] = Character.toUpperCase(c);
        }
        return new String(chars);
    }
    public static String reverse(String str) { return str == null ? null : new StringBuilder(str).reverse().toString(); }
    public static String reverseDelimited(String str, char separatorChar) {
        if (str == null) return null;
        String[] parts = split(str, separatorChar);
        for (int i = 0, j = parts.length - 1; i < j; i++, j--) { String tmp = parts[i]; parts[i] = parts[j]; parts[j] = tmp; }
        return join(parts, separatorChar);
    }

    public static String defaultString(String str) { return str == null ? EMPTY : str; }
    public static String defaultString(String str, String defaultStr) { return str == null ? defaultStr : str; }
    public static String defaultIfEmpty(String str, String defaultStr) { return isEmpty(str) ? defaultStr : str; }
    public static String defaultIfBlank(String str, String defaultStr) { return isBlank(str) ? defaultStr : str; }

    public static boolean isNumeric(CharSequence cs) {
        if (isEmpty(cs)) return false;
        for (int i = 0; i < cs.length(); i++) { if (!Character.isDigit(cs.charAt(i))) return false; }
        return true;
    }
    public static boolean isNumericSpace(CharSequence cs) {
        if (isEmpty(cs)) return false;
        for (int i = 0; i < cs.length(); i++) { char c = cs.charAt(i); if (!Character.isDigit(c) && c != ' ') return false; }
        return true;
    }
    public static boolean isAlpha(CharSequence cs) {
        if (isEmpty(cs)) return false;
        for (int i = 0; i < cs.length(); i++) { if (!Character.isLetter(cs.charAt(i))) return false; }
        return true;
    }
    public static boolean isAlphanumeric(CharSequence cs) {
        if (isEmpty(cs)) return false;
        for (int i = 0; i < cs.length(); i++) { if (!Character.isLetterOrDigit(cs.charAt(i))) return false; }
        return true;
    }
    public static boolean isAlphaSpace(CharSequence cs) {
        if (isEmpty(cs)) return false;
        for (int i = 0; i < cs.length(); i++) { char c = cs.charAt(i); if (!Character.isLetter(c) && c != ' ') return false; }
        return true;
    }
    public static boolean isWhitespace(CharSequence cs) {
        if (cs == null) return false;
        if (cs.length() == 0) return true;
        for (int i = 0; i < cs.length(); i++) { if (!Character.isWhitespace(cs.charAt(i))) return false; }
        return true;
    }
    public static boolean isAllLowerCase(CharSequence cs) {
        if (isEmpty(cs)) return false;
        for (int i = 0; i < cs.length(); i++) { if (!Character.isLowerCase(cs.charAt(i))) return false; }
        return true;
    }
    public static boolean isAllUpperCase(CharSequence cs) {
        if (isEmpty(cs)) return false;
        for (int i = 0; i < cs.length(); i++) { if (!Character.isUpperCase(cs.charAt(i))) return false; }
        return true;
    }

    public static int countMatches(CharSequence str, CharSequence sub) {
        if (isEmpty(str) || isEmpty(sub)) return 0;
        int count = 0; int idx = 0; String s = str.toString(); String q = sub.toString();
        while ((idx = s.indexOf(q, idx)) != INDEX_NOT_FOUND) { count++; idx += q.length(); }
        return count;
    }
    public static int countMatches(CharSequence str, char ch) {
        if (isEmpty(str)) return 0;
        int count = 0;
        for (int i = 0; i < str.length(); i++) { if (str.charAt(i) == ch) count++; }
        return count;
    }

    public static String abbreviate(String str, int maxWidth) { return abbreviate(str, 0, maxWidth); }
    public static String abbreviate(String str, int offset, int maxWidth) {
        if (str == null) return null;
        if (maxWidth < 4) throw new IllegalArgumentException("Minimum abbreviation width is 4");
        if (str.length() <= maxWidth) return str;
        if (offset > str.length()) offset = str.length();
        if (str.length() - offset < maxWidth - 3) offset = str.length() - (maxWidth - 3);
        if (offset <= 3) return str.substring(0, maxWidth - 3) + "...";
        if (maxWidth < 7) throw new IllegalArgumentException("Minimum abbreviation width with offset is 7");
        if (offset + maxWidth - 3 < str.length()) return "..." + abbreviate(str.substring(offset), 0, maxWidth - 3);
        return "..." + str.substring(str.length() - (maxWidth - 3));
    }
    public static String truncate(String str, int maxWidth) { return truncate(str, 0, maxWidth); }
    public static String truncate(String str, int offset, int maxWidth) {
        if (offset < 0) throw new IllegalArgumentException("offset cannot be negative");
        if (maxWidth < 0) throw new IllegalArgumentException("maxWidth cannot be negative");
        if (str == null) return null;
        if (offset > str.length()) offset = str.length();
        if (str.length() > offset) return str.substring(offset, Math.min(offset + maxWidth, str.length()));
        return EMPTY;
    }
    public static String wrap(String str, String wrapWith) {
        if (isEmpty(str) || wrapWith == null) return str;
        return wrapWith + str + wrapWith;
    }
    public static String wrapIfMissing(String str, String wrapWith) {
        if (isEmpty(str) || isEmpty(wrapWith)) return str;
        StringBuilder sb = new StringBuilder();
        if (!startsWith(str, wrapWith)) sb.append(wrapWith);
        sb.append(str);
        if (!endsWith(str, wrapWith)) sb.append(wrapWith);
        return sb.toString();
    }
    public static String unwrap(String str, String wrapToken) {
        if (isEmpty(str) || isEmpty(wrapToken)) return str;
        if (startsWith(str, wrapToken) && endsWith(str, wrapToken) && str.length() >= 2 * wrapToken.length()) {
            return str.substring(wrapToken.length(), str.length() - wrapToken.length());
        }
        return str;
    }
    public static String chomp(String str) {
        if (isEmpty(str)) return str;
        if (str.length() == 1) { char ch = str.charAt(0); return (ch == '\r' || ch == '\n') ? EMPTY : str; }
        int last = str.length() - 1; char c = str.charAt(last);
        if (c == '\n') { if (str.charAt(last - 1) == '\r') return str.substring(0, last - 1); return str.substring(0, last); }
        return c == '\r' ? str.substring(0, last) : str;
    }
    public static String chop(String str) {
        if (str == null) return null;
        int len = str.length();
        if (len == 0) return str;
        if (len == 1) return EMPTY;
        if (len >= 2 && str.charAt(len - 2) == '\r' && str.charAt(len - 1) == '\n') return str.substring(0, len - 2);
        return str.substring(0, len - 1);
    }
    public static String getCommonPrefix(String... strs) {
        if (strs == null || strs.length == 0) return EMPTY;
        int minLen = Integer.MAX_VALUE;
        for (String s : strs) minLen = Math.min(minLen, s == null ? 0 : s.length());
        if (minLen == 0) return EMPTY;
        for (int i = 0; i < minLen; i++) {
            char c = strs[0].charAt(i);
            for (int j = 1; j < strs.length; j++) { if (strs[j].charAt(i) != c) return strs[0].substring(0, i); }
        }
        return strs[0].substring(0, minLen);
    }
    public static String difference(String str1, String str2) {
        if (str1 == null) return str2;
        if (str2 == null) return str1;
        int at = indexOfDifference(str1, str2);
        return at == -1 ? EMPTY : str2.substring(at);
    }
    public static int indexOfDifference(CharSequence cs1, CharSequence cs2) {
        if (cs1 == cs2) return -1;
        if (cs1 == null || cs2 == null) return 0;
        int i;
        for (i = 0; i < cs1.length() && i < cs2.length(); ++i) { if (cs1.charAt(i) != cs2.charAt(i)) break; }
        if (i < cs2.length() || i < cs1.length()) return i;
        return -1;
    }
}
