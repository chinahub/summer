package cn.jiebaba.summer.test.util;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.core.util.StringUtil;

import java.util.Arrays;
import java.util.List;

public class StringUtilTest {

    @Test
    public void emptyAndBlank() {
        Assert.assertTrue(StringUtil.isEmpty(null));
        Assert.assertTrue(StringUtil.isEmpty(""));
        Assert.assertFalse(StringUtil.isEmpty(" "));
        Assert.assertTrue(StringUtil.isBlank(null));
        Assert.assertTrue(StringUtil.isBlank("   "));
        Assert.assertFalse(StringUtil.isBlank(" a "));
        Assert.assertTrue(StringUtil.isNotEmpty("x"));
        Assert.assertTrue(StringUtil.isNotBlank("x"));
    }

    @Test
    public void trimAndStrip() {
        Assert.assertNull(StringUtil.trim(null));
        Assert.assertEquals("x", StringUtil.trim("  x  "));
        Assert.assertNull(StringUtil.trimToNull("   "));
        Assert.assertEquals("", StringUtil.trimToEmpty(null));
        Assert.assertEquals("x", StringUtil.strip("\tx\t"));
    }

    @Test
    public void equalityAndCompare() {
        Assert.assertTrue(StringUtil.equals("a", "a"));
        Assert.assertFalse(StringUtil.equals("a", "b"));
        Assert.assertTrue(StringUtil.equalsIgnoreCase("Ab", "aB"));
        Assert.assertTrue(StringUtil.equalsAny("b", "a", "b", "c"));
        Assert.assertTrue(StringUtil.compare("a", "b") < 0);
        Assert.assertTrue(StringUtil.compare(null, "b", false) > 0);
        Assert.assertTrue(StringUtil.compare(null, "b", true) < 0);
    }

    @Test
    public void startsAndEnds() {
        Assert.assertTrue(StringUtil.startsWith("abc", "ab"));
        Assert.assertTrue(StringUtil.startsWithIgnoreCase("ABC", "ab"));
        Assert.assertTrue(StringUtil.startsWithAny("abc", "x", "ab"));
        Assert.assertTrue(StringUtil.endsWith("abc", "bc"));
        Assert.assertTrue(StringUtil.endsWithIgnoreCase("ABC", "bc"));
        Assert.assertFalse(StringUtil.startsWith(null, "a"));
    }

    @Test
    public void contains() {
        Assert.assertTrue(StringUtil.contains("abc", 'b'));
        Assert.assertTrue(StringUtil.contains("abc", "bc"));
        Assert.assertTrue(StringUtil.containsIgnoreCase("aBc", "BC"));
        Assert.assertTrue(StringUtil.containsAny("abc", "x", "b"));
        Assert.assertTrue(StringUtil.containsAny("abc", 'z', 'b'));
        Assert.assertTrue(StringUtil.containsOnly("aaa", 'a'));
        Assert.assertTrue(StringUtil.containsNone("abc", 'z'));
        Assert.assertFalse(StringUtil.containsWhitespace("abc"));
        Assert.assertTrue(StringUtil.containsWhitespace("a b"));
    }

    @Test
    public void indexOfOps() {
        Assert.assertEquals(1, StringUtil.indexOf("abc", 'b'));
        Assert.assertEquals(1, StringUtil.indexOf("abc", "bc"));
        Assert.assertEquals(1, StringUtil.indexOfIgnoreCase("ABC", "bc"));
        Assert.assertEquals(3, StringUtil.ordinalIndexOf("a-b-a-b", "-", 2));
        Assert.assertEquals(2, StringUtil.lastIndexOf("abc", "c"));
        Assert.assertEquals(StringUtil.INDEX_NOT_FOUND, StringUtil.indexOf("abc", "z"));
    }

    @Test
    public void substringOps() {
        Assert.assertEquals("bc", StringUtil.substring("abc", 1));
        Assert.assertEquals("ab", StringUtil.substring("abc", 0, 2));
        Assert.assertEquals("c", StringUtil.substring("abc", -1));
        Assert.assertEquals("ab", StringUtil.left("abc", 2));
        Assert.assertEquals("bc", StringUtil.right("abc", 2));
        Assert.assertEquals("b", StringUtil.mid("abc", 1, 1));
        Assert.assertEquals("ab", StringUtil.substringBefore("abc", "c"));
        Assert.assertEquals("c", StringUtil.substringAfter("abc", "b"));
        Assert.assertEquals("a.b", StringUtil.substringBeforeLast("a.b.c", "."));
        Assert.assertEquals("c", StringUtil.substringAfterLast("a.b.c", "."));
        Assert.assertEquals("b", StringUtil.substringBetween("a[b]c", "[", "]"));
        assertArrayEquals(new String[]{"b", "d"}, StringUtil.substringsBetween("[b]x[d]", "[", "]"));
    }

    @Test
    public void splitAndJoin() {
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtil.split("a b c"));
        assertArrayEquals(new String[]{"a", "", "c"}, StringUtil.splitPreserveAllTokens("a,,c", ","));
        assertArrayEquals(new String[]{"a", "b", "c"}, StringUtil.split("a.b.c", "."));
        Assert.assertEquals("a-b-c", StringUtil.join(new String[]{"a", "b", "c"}, "-"));
        Assert.assertEquals("a,b,c", StringUtil.join(List.of("a", "b", "c"), ","));
        Assert.assertEquals("a/b/c", StringUtil.join(new Object[]{"a", "b", "c"}, '/'));
    }

    @Test
    public void repeatAndPad() {
        Assert.assertEquals("abab", StringUtil.repeat("ab", 2));
        Assert.assertEquals("aaa", StringUtil.repeat('a', 3));
        Assert.assertEquals("   x", StringUtil.leftPad("x", 4));
        Assert.assertEquals("x   ", StringUtil.rightPad("x", 4));
        Assert.assertEquals("00x", StringUtil.leftPad("x", 3, '0'));
        Assert.assertEquals("ababx", StringUtil.leftPad("x", 5, "ab"));
        Assert.assertEquals(" a  ", StringUtil.center("a", 4));
    }

    @Test
    public void replaceAndRemove() {
        Assert.assertEquals("XcXc", StringUtil.replace("abcabc", "ab", "X"));
        Assert.assertEquals("Xab", StringUtil.replaceOnce("abab", "ab", "X"));
        Assert.assertEquals("cdcd", StringUtil.replaceEach("abab", new String[]{"a", "b"}, new String[]{"c", "d"}));
        Assert.assertEquals("a", StringUtil.remove("abc", "bc"));
        Assert.assertEquals("bc", StringUtil.removeStart("abc", "a"));
        Assert.assertEquals("ab", StringUtil.removeEnd("abc", "c"));
        Assert.assertEquals("abc", StringUtil.deleteWhitespace(" a b c "));
        Assert.assertEquals("a b c", StringUtil.normalizeSpace("  a   b  c "));
    }

    @Test
    public void caseOps() {
        Assert.assertEquals("Hello", StringUtil.capitalize("hello"));
        Assert.assertEquals("hELLO", StringUtil.uncapitalize("HELLO"));
        Assert.assertEquals("ABC", StringUtil.upperCase("abc"));
        Assert.assertEquals("abc", StringUtil.lowerCase("ABC"));
        Assert.assertEquals("hELLO", StringUtil.swapCase("Hello"));
        Assert.assertEquals("cba", StringUtil.reverse("abc"));
        Assert.assertEquals("c.b.a", StringUtil.reverseDelimited("a.b.c", '.'));
    }

    @Test
    public void defaults() {
        Assert.assertEquals("", StringUtil.defaultString(null));
        Assert.assertEquals("d", StringUtil.defaultString(null, "d"));
        Assert.assertEquals("d", StringUtil.defaultIfEmpty("", "d"));
        Assert.assertEquals("d", StringUtil.defaultIfBlank("   ", "d"));
    }

    @Test
    public void checks() {
        Assert.assertTrue(StringUtil.isNumeric("123"));
        Assert.assertFalse(StringUtil.isNumeric("12a"));
        Assert.assertTrue(StringUtil.isNumericSpace("1 2 3"));
        Assert.assertTrue(StringUtil.isAlpha("abc"));
        Assert.assertTrue(StringUtil.isAlphanumeric("ab12"));
        Assert.assertTrue(StringUtil.isAlphaSpace("a b c"));
        Assert.assertTrue(StringUtil.isWhitespace("   "));
        Assert.assertTrue(StringUtil.isAllLowerCase("abc"));
        Assert.assertTrue(StringUtil.isAllUpperCase("ABC"));
    }

    @Test
    public void misc() {
        Assert.assertEquals(2, StringUtil.countMatches("abab", "ab"));
        Assert.assertEquals(2, StringUtil.countMatches("abab", 'a'));
        Assert.assertEquals("abc...", StringUtil.abbreviate("abcdefg", 6));
        Assert.assertEquals("abc", StringUtil.truncate("abcdef", 3));
        Assert.assertEquals("\"x\"", StringUtil.wrap("x", "\""));
        Assert.assertEquals("x", StringUtil.unwrap("\"x\"", "\""));
        Assert.assertEquals("ab", StringUtil.getCommonPrefix("abc", "abd"));
        Assert.assertEquals("c", StringUtil.difference("ab", "ac"));
        Assert.assertEquals("ab", StringUtil.chomp("ab\n"));
        Assert.assertEquals("ab", StringUtil.chop("abc"));
    }

    static void assertArrayEquals(String[] expected, String[] actual) {
        Assert.assertTrue(Arrays.equals(expected, actual),
                "expected " + Arrays.toString(expected) + " but was " + Arrays.toString(actual));
    }
}