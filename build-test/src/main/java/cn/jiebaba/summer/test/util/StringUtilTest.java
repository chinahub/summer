package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.util.StringUtil;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class StringUtilTest {

    @Test
    public void emptyAndBlank() {
        Assertions.assertTrue(StringUtil.isEmpty(null));
        Assertions.assertTrue(StringUtil.isEmpty(""));
        Assertions.assertFalse(StringUtil.isEmpty(" "));
        Assertions.assertTrue(StringUtil.isBlank(null));
        Assertions.assertTrue(StringUtil.isBlank("   "));
        Assertions.assertFalse(StringUtil.isBlank(" a "));
        Assertions.assertTrue(StringUtil.isNotEmpty("x"));
        Assertions.assertTrue(StringUtil.isNotBlank("x"));
    }

    @Test
    public void trimAndStrip() {
        Assertions.assertNull(StringUtil.trim(null));
        Assertions.assertEquals("x", StringUtil.trim("  x  "));
        Assertions.assertNull(StringUtil.trimToNull("   "));
        Assertions.assertEquals("", StringUtil.trimToEmpty(null));
        Assertions.assertEquals("x", StringUtil.strip("\tx\t"));
    }

    @Test
    public void equalityAndCompare() {
        Assertions.assertTrue(StringUtil.equals("a", "a"));
        Assertions.assertFalse(StringUtil.equals("a", "b"));
        Assertions.assertTrue(StringUtil.equalsIgnoreCase("Ab", "aB"));
        Assertions.assertTrue(StringUtil.equalsAny("b", "a", "b", "c"));
        Assertions.assertTrue(StringUtil.compare("a", "b") < 0);
        Assertions.assertTrue(StringUtil.compare(null, "b", false) > 0);
        Assertions.assertTrue(StringUtil.compare(null, "b", true) < 0);
    }

    @Test
    public void startsAndEnds() {
        Assertions.assertTrue(StringUtil.startsWith("abc", "ab"));
        Assertions.assertTrue(StringUtil.startsWithIgnoreCase("ABC", "ab"));
        Assertions.assertTrue(StringUtil.startsWithAny("abc", "x", "ab"));
        Assertions.assertTrue(StringUtil.endsWith("abc", "bc"));
        Assertions.assertTrue(StringUtil.endsWithIgnoreCase("ABC", "bc"));
        Assertions.assertFalse(StringUtil.startsWith(null, "a"));
    }

    @Test
    public void contains() {
        Assertions.assertTrue(StringUtil.contains("abc", 'b'));
        Assertions.assertTrue(StringUtil.contains("abc", "bc"));
        Assertions.assertTrue(StringUtil.containsIgnoreCase("aBc", "BC"));
        Assertions.assertTrue(StringUtil.containsAny("abc", "x", "b"));
        Assertions.assertTrue(StringUtil.containsAny("abc", 'z', 'b'));
        Assertions.assertTrue(StringUtil.containsOnly("aaa", 'a'));
        Assertions.assertTrue(StringUtil.containsNone("abc", 'z'));
        Assertions.assertFalse(StringUtil.containsWhitespace("abc"));
        Assertions.assertTrue(StringUtil.containsWhitespace("a b"));
    }

    @Test
    public void indexOfOps() {
        Assertions.assertEquals(1, StringUtil.indexOf("abc", 'b'));
        Assertions.assertEquals(1, StringUtil.indexOf("abc", "bc"));
        Assertions.assertEquals(1, StringUtil.indexOfIgnoreCase("ABC", "bc"));
        Assertions.assertEquals(3, StringUtil.ordinalIndexOf("a-b-a-b", "-", 2));
        Assertions.assertEquals(2, StringUtil.lastIndexOf("abc", "c"));
        Assertions.assertEquals(StringUtil.INDEX_NOT_FOUND, StringUtil.indexOf("abc", "z"));
    }

    @Test
    public void substringOps() {
        Assertions.assertEquals("bc", StringUtil.substring("abc", 1));
        Assertions.assertEquals("ab", StringUtil.substring("abc", 0, 2));
        Assertions.assertEquals("c", StringUtil.substring("abc", -1));
        Assertions.assertEquals("ab", StringUtil.left("abc", 2));
        Assertions.assertEquals("bc", StringUtil.right("abc", 2));
        Assertions.assertEquals("b", StringUtil.mid("abc", 1, 1));
        Assertions.assertEquals("ab", StringUtil.substringBefore("abc", "c"));
        Assertions.assertEquals("c", StringUtil.substringAfter("abc", "b"));
        Assertions.assertEquals("a.b", StringUtil.substringBeforeLast("a.b.c", "."));
        Assertions.assertEquals("c", StringUtil.substringAfterLast("a.b.c", "."));
        Assertions.assertEquals("b", StringUtil.substringBetween("a[b]c", "[", "]"));
        assertArrayEquals(new String[]{"b", "d"}, StringUtil.substringsBetween("[b]x[d]", "[", "]"));
    }

    @Test
    public void splitAndJoin() {
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtil.split("a b c"));
        assertArrayEquals(new String[]{"a", "", "c"}, StringUtil.splitPreserveAllTokens("a,,c", ","));
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtil.split("a.b.c", "."));
        Assertions.assertEquals("a-b-c", StringUtil.join(new String[]{"a", "b", "c"}, "-"));
        Assertions.assertEquals("a,b,c", StringUtil.join(List.of("a", "b", "c"), ","));
        Assertions.assertEquals("a/b/c", StringUtil.join(new Object[]{"a", "b", "c"}, '/'));
    }

    @Test
    public void repeatAndPad() {
        Assertions.assertEquals("abab", StringUtil.repeat("ab", 2));
        Assertions.assertEquals("aaa", StringUtil.repeat('a', 3));
        Assertions.assertEquals("   x", StringUtil.leftPad("x", 4));
        Assertions.assertEquals("x   ", StringUtil.rightPad("x", 4));
        Assertions.assertEquals("00x", StringUtil.leftPad("x", 3, '0'));
        Assertions.assertEquals("ababx", StringUtil.leftPad("x", 5, "ab"));
        Assertions.assertEquals(" a  ", StringUtil.center("a", 4));
    }

    @Test
    public void replaceAndRemove() {
        Assertions.assertEquals("XcXc", StringUtil.replace("abcabc", "ab", "X"));
        Assertions.assertEquals("Xab", StringUtil.replaceOnce("abab", "ab", "X"));
        Assertions.assertEquals("cdcd", StringUtil.replaceEach("abab", new String[]{"a", "b"}, new String[]{"c", "d"}));
        Assertions.assertEquals("a", StringUtil.remove("abc", "bc"));
        Assertions.assertEquals("bc", StringUtil.removeStart("abc", "a"));
        Assertions.assertEquals("ab", StringUtil.removeEnd("abc", "c"));
        Assertions.assertEquals("abc", StringUtil.deleteWhitespace(" a b c "));
        Assertions.assertEquals("a b c", StringUtil.normalizeSpace("  a   b  c "));
    }

    @Test
    public void caseOps() {
        Assertions.assertEquals("Hello", StringUtil.capitalize("hello"));
        Assertions.assertEquals("hELLO", StringUtil.uncapitalize("HELLO"));
        Assertions.assertEquals("ABC", StringUtil.upperCase("abc"));
        Assertions.assertEquals("abc", StringUtil.lowerCase("ABC"));
        Assertions.assertEquals("hELLO", StringUtil.swapCase("Hello"));
        Assertions.assertEquals("cba", StringUtil.reverse("abc"));
        Assertions.assertEquals("c.b.a", StringUtil.reverseDelimited("a.b.c", '.'));
    }

    @Test
    public void defaults() {
        Assertions.assertEquals("", StringUtil.defaultString(null));
        Assertions.assertEquals("d", StringUtil.defaultString(null, "d"));
        Assertions.assertEquals("d", StringUtil.defaultIfEmpty("", "d"));
        Assertions.assertEquals("d", StringUtil.defaultIfBlank("   ", "d"));
    }

    @Test
    public void checks() {
        Assertions.assertTrue(StringUtil.isNumeric("123"));
        Assertions.assertFalse(StringUtil.isNumeric("12a"));
        Assertions.assertTrue(StringUtil.isNumericSpace("1 2 3"));
        Assertions.assertTrue(StringUtil.isAlpha("abc"));
        Assertions.assertTrue(StringUtil.isAlphanumeric("ab12"));
        Assertions.assertTrue(StringUtil.isAlphaSpace("a b c"));
        Assertions.assertTrue(StringUtil.isWhitespace("   "));
        Assertions.assertTrue(StringUtil.isAllLowerCase("abc"));
        Assertions.assertTrue(StringUtil.isAllUpperCase("ABC"));
    }

    @Test
    public void misc() {
        Assertions.assertEquals(2, StringUtil.countMatches("abab", "ab"));
        Assertions.assertEquals(2, StringUtil.countMatches("abab", 'a'));
        Assertions.assertEquals("abc...", StringUtil.abbreviate("abcdefg", 6));
        Assertions.assertEquals("abc", StringUtil.truncate("abcdef", 3));
        Assertions.assertEquals("\"x\"", StringUtil.wrap("x", "\""));
        Assertions.assertEquals("x", StringUtil.unwrap("\"x\"", "\""));
        Assertions.assertEquals("ab", StringUtil.getCommonPrefix("abc", "abd"));
        Assertions.assertEquals("c", StringUtil.difference("ab", "ac"));
        Assertions.assertEquals("ab", StringUtil.chomp("ab\n"));
        Assertions.assertEquals("ab", StringUtil.chop("abc"));
    }

    static void assertArrayEquals(String[] expected, String[] actual) {
        Assertions.assertTrue(Arrays.equals(expected, actual),
                "expected " + Arrays.toString(expected) + " but was " + Arrays.toString(actual));
    }
}