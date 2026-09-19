package cn.jiebaba.summer.office.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TrueType 字体解析器：纯 JDK 实现，解析 cmap（Unicode->字形 id）、hmtx（字形宽度）、
 * head/hhea/maxp/OS-2（度量），供 {@link PdfWriter} 内嵌 Type0/CIDFontType2 字体支持 CJK。
 * <p>仅解析生成 PDF 所需的字段，不做字形轮廓子集化（全字体嵌入）。
 * 支持 cmap format 4（BMP）与 format 12（全 Unicode），优先 (3,10)/(3,1)/(0,*) 子表。
 */
final class TtfFont {

    private final byte[] data;
    private final int unitsPerEm;
    private final int numGlyphs;
    private final int ascent;
    private final int descent;
    private final int capHeight;
    private final int[] bbox;
    private final int[] widths;
    private final Map<Integer, Integer> codeToGlyph;
    private final String family;

    private TtfFont(byte[] data, int unitsPerEm, int numGlyphs, int ascent, int descent,
                    int capHeight, int[] bbox, int[] widths, Map<Integer, Integer> codeToGlyph, String family) {
        this.data = data;
        this.unitsPerEm = unitsPerEm;
        this.numGlyphs = numGlyphs;
        this.ascent = ascent;
        this.descent = descent;
        this.capHeight = capHeight;
        this.bbox = bbox;
        this.widths = widths;
        this.codeToGlyph = codeToGlyph;
        this.family = family;
    }

    /** 从字节数组加载并解析 TTF。 */
    static TtfFont load(byte[] ttf) throws IOException {
        Reader r = new Reader(ttf);
        // TTC（TrueType Collection）：识别 ttcf 魔数后取第一个字体偏移作为解析基址；
        // 表目录位于 base+4(numTables)/base+12(记录)，各表 offset 字段为文件绝对偏移。
        int base = 0;
        if (ttf.length >= 12 && r.tag(0).equals("ttcf")) {
            base = (int) r.u32(12);
        }
        int numTables = r.u16(base + 4);
        Map<String, int[]> tables = new LinkedHashMap<>();
        for (int i = 0; i < numTables; i++) {
            int rec = base + 12 + i * 16;
            String tag = r.tag(rec);
            int offset = (int) r.u32(rec + 8);
            int length = (int) r.u32(rec + 12);
            tables.put(tag, new int[]{offset, length});
        }
        if (!tables.containsKey("head") || !tables.containsKey("maxp") || !tables.containsKey("cmap")) {
            throw new IOException("非法 TTF：缺少 head/maxp/cmap 表");
        }
        int[] head = tables.get("head");
        int unitsPerEm = r.u16(head[0] + 18);
        int[] bbox = {r.s16(head[0] + 36), r.s16(head[0] + 38), r.s16(head[0] + 40), r.s16(head[0] + 42)};

        int[] maxp = tables.get("maxp");
        int numGlyphs = r.u16(maxp[0] + 4);

        int[] hhea = tables.get("hhea");
        int numOfLongHorMetrics = hhea != null ? r.u16(hhea[0] + 34) : numGlyphs;
        int hheaAscent = hhea != null ? r.s16(hhea[0] + 4) : bbox[3];
        int hheaDescent = hhea != null ? r.s16(hhea[0] + 6) : bbox[1];

        int[] os2 = tables.get("OS/2");
        int ascent = os2 != null ? r.s16(os2[0] + 68) : hheaAscent;
        int descent = os2 != null ? r.s16(os2[0] + 70) : hheaDescent;
        int capHeight = os2 != null ? r.s16(os2[0] + 88) : 0;

        int[] widths = parseWidths(r, tables.get("hmtx"), numOfLongHorMetrics, numGlyphs);
        Map<Integer, Integer> codeToGlyph = parseCmap(r, tables.get("cmap"));
        String family = parseFamily(r, tables.get("name"));

        return new TtfFont(ttf, unitsPerEm, numGlyphs, ascent, descent, capHeight, bbox, widths, codeToGlyph, family);
    }

    /** 从文件路径加载 TTF。 */
    static TtfFont load(Path path) throws IOException {
        return load(Files.readAllBytes(path));
    }

    /** 返回码点对应的字形 id；未找到返回 0（.notdef）。 */
    int glyphId(int codepoint) {
        return codeToGlyph.getOrDefault(codepoint, 0);
    }

    /** 返回字形前进宽度（字体单位）。 */
    int glyphWidth(int glyphId) {
        if (glyphId < 0 || glyphId >= widths.length) {
            return 0;
        }
        return widths[glyphId];
    }

    int unitsPerEm() { return unitsPerEm; }
    int ascent() { return ascent; }
    int descent() { return descent; }
    int capHeight() { return capHeight; }
    int[] bbox() { return bbox; }
    byte[] bytes() { return data; }

    /** 返回字体族名（清理为 PDF BaseFont 合法名：仅保留字母数字与连字符）。 */
    String family() {
        if (family == null || family.isEmpty()) {
            return "EmbeddedCJK";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < family.length(); i++) {
            char c = family.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-') {
                sb.append(c);
            }
        }
        String s = sb.toString();
        return s.isEmpty() ? "EmbeddedCJK" : s;
    }

    // ==================== 表解析 ====================

    /** 解析 hmtx：numOfLongHorMetrics 个显式宽度，其余继承末位宽度。 */
    private static int[] parseWidths(Reader r, int[] hmtx, int numOfLongHorMetrics, int numGlyphs) {
        int[] widths = new int[numGlyphs];
        if (hmtx == null) {
            return widths;
        }
        int base = hmtx[0];
        int last = 0;
        for (int i = 0; i < numGlyphs; i++) {
            if (i < numOfLongHorMetrics) {
                last = r.u16(base + i * 4);
            }
            widths[i] = last;
        }
        return widths;
    }

    /** 解析 cmap：选择最佳子表，按 format 4 或 12 构建 codepoint->glyphId 映射。 */
    private static Map<Integer, Integer> parseCmap(Reader r, int[] cmap) {
        Map<Integer, Integer> map = new HashMap<>();
        if (cmap == null) {
            return map;
        }
        int base = cmap[0];
        int numTables = r.u16(base + 2);
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < numTables; i++) {
            int rec = base + 4 + i * 8;
            int platform = r.u16(rec);
            int encoding = r.u16(rec + 2);
            int offset = (int) r.u32(rec + 4);
            int score = scoreSubtable(platform, encoding);
            if (score > bestScore) {
                bestScore = score;
                best = base + offset;
            }
        }
        if (best < 0) {
            return map;
        }
        int format = r.u16(best);
        if (format == 4) {
            parseCmapFormat4(r, best, map);
        } else if (format == 12) {
            parseCmapFormat12(r, best, map);
        } else if (format == 6) {
            parseCmapFormat6(r, best, map);
        }
        return map;
    }

    /** 子表优先级评分：(3,10)>(3,1)>(0,*)；其余 -1 不采用。 */
    private static int scoreSubtable(int platform, int encoding) {
        if (platform == 3 && encoding == 10) return 5;
        if (platform == 3 && encoding == 1) return 4;
        if (platform == 0) return 3;
        return -1;
    }

    /** 解析 cmap format 4（BMP）：按段计算 codepoint->glyphId。 */
    private static void parseCmapFormat4(Reader r, int start, Map<Integer, Integer> map) {
        int segCountX2 = r.u16(start + 6);
        int segCount = segCountX2 / 2;
        int endCodePos = start + 14;
        int startCodePos = endCodePos + segCountX2 + 2;
        int idDeltaPos = startCodePos + segCountX2;
        int idRangeOffsetPos = idDeltaPos + segCountX2;
        for (int i = 0; i < segCount; i++) {
            int end = r.u16(endCodePos + i * 2);
            int beg = r.u16(startCodePos + i * 2);
            int delta = r.s16(idDeltaPos + i * 2);
            int rangeOff = r.u16(idRangeOffsetPos + i * 2);
            if (end == 0xFFFF) {
                continue;
            }
            for (int cp = beg; cp <= end; cp++) {
                int gid;
                if (rangeOff == 0) {
                    gid = (cp + delta) & 0xFFFF;
                } else {
                    int addr = idRangeOffsetPos + i * 2 + rangeOff + (cp - beg) * 2;
                    int g = r.u16(addr);
                    gid = g == 0 ? 0 : (g + delta) & 0xFFFF;
                }
                if (gid != 0) {
                    map.put(cp, gid);
                }
            }
        }
    }

    /** 解析 cmap format 12（全 Unicode）：按组计算。 */
    private static void parseCmapFormat12(Reader r, int start, Map<Integer, Integer> map) {
        long numGroups = r.u32(start + 12) & 0xFFFFFFFFL;
        int groupPos = start + 16;
        for (long g = 0; g < numGroups; g++) {
            int pos = (int) (groupPos + g * 12);
            long beg = r.u32(pos) & 0xFFFFFFFFL;
            long end = r.u32(pos + 4) & 0xFFFFFFFFL;
            long startGid = r.u32(pos + 8) & 0xFFFFFFFFL;
            for (long cp = beg; cp <= end; cp++) {
                if (cp <= Integer.MAX_VALUE) {
                    map.put((int) cp, (int) (startGid + (cp - beg)));
                }
            }
        }
    }

    /** 解析 cmap format 6（直接映射，较罕见）：firstCode..firstCode+length-1 -> glyphId..。 */
    private static void parseCmapFormat6(Reader r, int start, Map<Integer, Integer> map) {
        int first = r.u16(start + 6);
        int count = r.u16(start + 8);
        for (int i = 0; i < count; i++) {
            int gid = r.u16(start + 10 + i * 2);
            if (gid != 0) {
                map.put(first + i, gid);
            }
        }
    }

    /** 解析 name 表的字体族名（nameID=1，优先 Windows 平台 UTF-16BE）。 */
    private static String parseFamily(Reader r, int[] name) {
        if (name == null) {
            return null;
        }
        int base = name[0];
        int count = r.u16(base + 2);
        int storage = base + r.u16(base + 4);
        for (int i = 0; i < count; i++) {
            int rec = base + 6 + i * 12;
            int platform = r.u16(rec);
            int nameId = r.u16(rec + 6);
            int length = r.u16(rec + 8);
            int offset = r.u16(rec + 10);
            if (nameId == 1 && (platform == 3 || platform == 0)) {
                return r.utf16(storage + offset, length);
            }
        }
        return null;
    }

    /** 字节读取器：按偏移读取 u16/s16/u32/tag/UTF-16BE。 */
    private static final class Reader {
        private final byte[] d;

        Reader(byte[] d) {
            this.d = d;
        }

        int u16(int off) {
            return ((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF);
        }

        int s16(int off) {
            return (short) u16(off);
        }

        long u32(int off) {
            return ((long) (d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16)
                    | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
        }

        String tag(int off) {
            return new String(d, off, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        }

        String utf16(int off, int len) {
            return new String(d, off, len, java.nio.charset.StandardCharsets.UTF_16BE);
        }
    }
}
