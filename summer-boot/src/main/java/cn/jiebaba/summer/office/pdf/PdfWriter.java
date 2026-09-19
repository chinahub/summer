package cn.jiebaba.summer.office.pdf;

import cn.jiebaba.summer.office.OfficeWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.Deflater;

/**
 * PDF 写入器：纯 JDK 实现，零第三方依赖。
 * <p>默认使用标准 Type1 字体 Helvetica（仅 Latin/WinAnsi）；
 * 通过 {@link #withFont(byte[])} 嵌入 TrueType 字体后，可输出中文/CJK 文本，
 * 采用 Type0 + CIDFontType2 + FontDescriptor + FontFile2 + ToUnicode 完整结构。
 * <p><b>实现说明</b>：采用动态对象模型（按需分配对象号、统一写 xref），
 * 文本以 2 字节字形 id（Identity-H）十六进制编码写入内容流；
 * 字体整体嵌入（非子集化），子集化留作后续体积优化。
 *
 * <pre>{@code
 * byte[] pdf = new PdfWriter()
 *         .withFont(Files.readAllBytes(Path.of("C:/Windows/Fonts/simhei.ttf")))
 *         .write("你好，PDF 中文！");
 * }</pre>
 */
public class PdfWriter implements OfficeWriter {

    /** A4 纵向页面宽度（磅，1 磅 = 1/72 英寸）。 */
    private static final int PAGE_WIDTH = 595;
    /** A4 纵向页面高度（磅）。 */
    private static final int PAGE_HEIGHT = 842;
    /** 页边距（磅）。 */
    private static final int MARGIN = 50;
    /** 字号（磅）。 */
    private static final int FONT_SIZE = 12;
    /** 行高（磅）。 */
    private static final int LINE_HEIGHT = 16;
    /** PDF 字体度量单位（1/1000 文本空间）。 */
    private static final int PDF_UNITS = 1000;

    /** 嵌入字体原始字节（可空，空时回退 Helvetica）。 */
    private byte[] fontBytes;
    /** 已解析的 TrueType 字体（可空）。 */
    private TtfFont ttf;

    public PdfWriter() {
    }

    /** 嵌入指定 TTF 字体以支持中文/CJK；返回自身以支持链式调用。 */
    public PdfWriter withFont(byte[] ttfBytes) throws IOException {
        this.fontBytes = ttfBytes;
        this.ttf = TtfFont.load(ttfBytes);
        return this;
    }

    /** 从文件路径加载并嵌入 TTF 字体。 */
    public PdfWriter withFont(Path path) throws IOException {
        return withFont(Files.readAllBytes(path));
    }

    @Override
    public byte[] write(String content) throws IOException {
        return ttf != null ? writeWithEmbeddedFont(content) : writeHelvetica(content);
    }

    // ==================== Helvetica 回退路径（仅 Latin） ====================

    /**
     * 使用标准 Helvetica 字体生成 PDF：固定对象编号、文本按换行拆分逐行 Tj。
     * 仅支持 WinAnsi 字符集，非 Latin 字符替换为 {@code ?}。
     */
    private static byte[] writeHelvetica(String content) {
        String text = content == null ? "" : content;
        String[] allLines = text.split("\n", -1);
        int linesPerPage = Math.max(1, (PAGE_HEIGHT - 2 * MARGIN) / LINE_HEIGHT);

        List<List<String>> pages = paginate(allLines, linesPerPage);
        int pageCount = pages.size();
        int fontObjNum = 3 + pageCount * 2;
        int totalObjs = fontObjNum + 1;
        long[] offsets = new long[totalObjs];

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        writeAscii(pdf, "%PDF-1.4\n");
        pdf.writeBytes(new byte[]{0x25, (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, 0x0A});

        offsets[1] = pdf.size();
        writeAscii(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets[2] = pdf.size();
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            if (i > 0) {
                kids.append(' ');
            }
            kids.append(3 + i * 2).append(" 0 R");
        }
        writeAscii(pdf, "2 0 obj\n<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>\nendobj\n");

        for (int p = 0; p < pageCount; p++) {
            int pageObj = 3 + p * 2;
            int contentObj = 4 + p * 2;

            offsets[pageObj] = pdf.size();
            writeAscii(pdf, pageObj + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    + PAGE_WIDTH + " " + PAGE_HEIGHT + "] /Resources << /Font << /F1 "
                    + fontObjNum + " 0 R >> >> /Contents " + contentObj + " 0 R >>\nendobj\n");

            byte[] stream = buildHelveticaContent(pages.get(p)).getBytes(StandardCharsets.ISO_8859_1);
            offsets[contentObj] = pdf.size();
            writeAscii(pdf, contentObj + " 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
            pdf.writeBytes(stream);
            writeAscii(pdf, "\nendstream\nendobj\n");
        }

        offsets[fontObjNum] = pdf.size();
        writeAscii(pdf, fontObjNum + " 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica"
                + " /Encoding /WinAnsiEncoding >>\nendobj\n");

        long xrefOffset = pdf.size();
        writeAscii(pdf, "xref\n0 " + totalObjs + "\n");
        writeAscii(pdf, "0000000000 65535 f \n");
        for (int i = 1; i < totalObjs; i++) {
            writeAscii(pdf, String.format("%010d 00000 n \n", offsets[i]));
        }
        writeAscii(pdf, "trailer\n<< /Size " + totalObjs + " /Root 1 0 R >>\nstartxref\n"
                + xrefOffset + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    /** 将所有行按每页行数划分为多页。 */
    private static List<List<String>> paginate(String[] lines, int linesPerPage) {
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < lines.length; i += linesPerPage) {
            int end = Math.min(i + linesPerPage, lines.length);
            pages.add(Arrays.asList(Arrays.copyOfRange(lines, i, end)));
        }
        if (pages.isEmpty()) {
            pages.add(List.of(""));
        }
        return pages;
    }

    /** 构建单页内容流（Helvetica）：BT 块内逐行 Td 定位 + Tj 显示文本。 */
    private static String buildHelveticaContent(List<String> lines) {
        StringBuilder sb = new StringBuilder(128 + lines.size() * 32);
        sb.append("BT\n/F1 ").append(FONT_SIZE).append(" Tf\n");
        sb.append(MARGIN).append(' ').append(PAGE_HEIGHT - MARGIN).append(" Td\n");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("0 ").append(-LINE_HEIGHT).append(" Td\n");
            }
            sb.append('(').append(escapePdfString(lines.get(i))).append(") Tj\n");
        }
        sb.append("ET");
        return sb.toString();
    }

    /** 转义 PDF 文本字符串中的特殊字符；非 WinAnsi 字符替换为 {@code ?}。 */
    private static String escapePdfString(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 4);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == ')') {
                sb.append('\\').append(c);
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '\r') {
                continue;
            } else if (c >= 32 && c <= 255) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    // ==================== 嵌入 TTF 字体路径（Type0 + CID） ====================

    /**
     * 使用嵌入 TrueType 字体生成 PDF：动态分配对象号，文本以字形 id 十六进制编码，
     * 收集已用字形以构造 W 宽度数组与 ToUnicode 反向映射。
     */
    private byte[] writeWithEmbeddedFont(String content) throws IOException {
        String text = content == null ? "" : content;
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        List<String> allLines = wrapLines(text);
        int linesPerPage = Math.max(1, (PAGE_HEIGHT - 2 * MARGIN) / LINE_HEIGHT);
        List<List<String>> pages = paginate(allLines.toArray(new String[0]), linesPerPage);
        int pageCount = pages.size();

        // 收集已用字形 id -> 源码点（用于 W 数组与 ToUnicode）
        TreeMap<Integer, Integer> gidToCp = new TreeMap<>();
        List<byte[]> contentStreams = new ArrayList<>(pageCount);
        for (List<String> page : pages) {
            contentStreams.add(buildCidContent(page, gidToCp));
        }

        // 动态对象模型：索引 0 为空闲项，1..n 为对象体（不含 obj/endobj 包装）
        List<byte[]> objs = new ArrayList<>();
        objs.add(new byte[0]);

        int catalogObj = alloc(objs);
        int pagesObj = alloc(objs);
        int[] pageObjs = new int[pageCount];
        int[] contentObjs = new int[pageCount];
        for (int p = 0; p < pageCount; p++) {
            pageObjs[p] = alloc(objs);
            contentObjs[p] = alloc(objs);
        }
        int type0Obj = alloc(objs);
        int cidObj = alloc(objs);
        int fdescObj = alloc(objs);
        int ff2Obj = alloc(objs);
        int touniObj = alloc(objs);

        // Catalog
        setObj(objs, catalogObj, "<< /Type /Catalog /Pages " + pagesObj + " 0 R >>");

        // Pages
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            if (i > 0) {
                kids.append(' ');
            }
            kids.append(pageObjs[i]).append(" 0 R");
        }
        setObj(objs, pagesObj, "<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>");

        // Page + Content 对象
        for (int p = 0; p < pageCount; p++) {
            setObj(objs, pageObjs[p], "<< /Type /Page /Parent " + pagesObj + " 0 R /MediaBox [0 0 "
                    + PAGE_WIDTH + " " + PAGE_HEIGHT + "] /Resources << /Font << /F1 "
                    + type0Obj + " 0 R >> >> /Contents " + contentObjs[p] + " 0 R >>");
            objs.set(contentObjs[p], streamObj("<< /Length " + contentStreams.get(p).length + " >>", contentStreams.get(p)));
        }

        // 字体相关对象
        String baseName = ttf.family();
        setObj(objs, type0Obj, "<< /Type /Font /Subtype /Type0 /BaseFont /" + baseName
                + " /Encoding /Identity-H /DescendantFonts [ " + cidObj + " 0 R ] /ToUnicode " + touniObj + " 0 R >>");
        objs.set(cidObj, cidFontBody(baseName, fdescObj, gidToCp).getBytes(StandardCharsets.ISO_8859_1));
        objs.set(fdescObj, fontDescriptorBody(baseName, ff2Obj).getBytes(StandardCharsets.ISO_8859_1));
        objs.set(ff2Obj, fontFileBody());
        objs.set(touniObj, toUnicodeBody(gidToCp));

        return assemblePdf(objs);
    }

    /** 分配一个对象号并占位（体后填）。 */
    private static int alloc(List<byte[]> objs) {
        objs.add(null);
        return objs.size() - 1;
    }

    /** 设置对象体为 ASCII 字符串。 */
    private static void setObj(List<byte[]> objs, int num, String body) {
        objs.set(num, body.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** 按字形宽度对源文本做按字符折行，返回折行后的行列表（保留显式换行）。 */
    private List<String> wrapLines(String text) {
        List<String> out = new ArrayList<>();
        double avail = PAGE_WIDTH - 2.0 * MARGIN;
        int upm = ttf.unitsPerEm();
        for (String src : text.split("\n", -1)) {
            if (src.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder cur = new StringBuilder();
            double curW = 0;
            int i = 0;
            while (i < src.length()) {
                int cp = src.codePointAt(i);
                int gid = ttf.glyphId(cp);
                double w = ttf.glyphWidth(gid) * (double) FONT_SIZE / upm;
                if (curW + w > avail && cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    curW = 0;
                }
                cur.appendCodePoint(cp);
                curW += w;
                i += Character.charCount(cp);
            }
            out.add(cur.toString());
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    /**
     * 构建单页 CID 内容流：逐行以字形 id 十六进制串 Tj，
     * 同时把用到的 (gid, cp) 记入 gidToCp 供字体子结构使用。
     */
    private byte[] buildCidContent(List<String> lines, TreeMap<Integer, Integer> gidToCp) {
        StringBuilder sb = new StringBuilder(128 + lines.size() * 48);
        sb.append("BT\n/F1 ").append(FONT_SIZE).append(" Tf\n");
        sb.append(MARGIN).append(' ').append(PAGE_HEIGHT - MARGIN).append(" Td\n");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("0 ").append(-LINE_HEIGHT).append(" Td\n");
            }
            String line = lines.get(i);
            sb.append('<');
            int j = 0;
            while (j < line.length()) {
                int cp = line.codePointAt(j);
                int gid = ttf.glyphId(cp);
                gidToCp.putIfAbsent(gid, cp);
                sb.append(String.format("%04X", gid & 0xFFFF));
                j += Character.charCount(cp);
            }
            sb.append("> Tj\n");
        }
        sb.append("ET");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** 构造 CIDFontType2 字典体：CIDSystemInfo/Identity 映射/默认宽度/W 宽度数组。 */
    private String cidFontBody(String baseName, int fdescObj, TreeMap<Integer, Integer> gidToCp) {
        int upm = ttf.unitsPerEm();
        int dw = scaled(ttf.glyphWidth(0), upm);
        if (dw <= 0) {
            dw = PDF_UNITS;
        }
        StringBuilder w = new StringBuilder("[");
        Integer prev = null;
        int runStart = -1;
        List<Integer> run = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : gidToCp.entrySet()) {
            int gid = e.getKey();
            int width = scaled(ttf.glyphWidth(gid), upm);
            if (runStart < 0) {
                runStart = gid;
                run.clear();
                run.add(width);
            } else if (gid == prev + 1) {
                run.add(width);
            } else {
                appendRun(w, runStart, run);
                runStart = gid;
                run.clear();
                run.add(width);
            }
            prev = gid;
        }
        if (runStart >= 0) {
            appendRun(w, runStart, run);
        }
        w.append(" ]");
        return "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /" + baseName
                + " /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>"
                + " /CIDToGIDMap /Identity /FontDescriptor " + fdescObj + " 0 R"
                + " /DW " + dw + " /W " + w + " >>";
    }

    /** 向 W 数组追加一段连续字形宽度：{@code firstGid [ w1 w2 ... ]}。 */
    private static void appendRun(StringBuilder w, int firstGid, List<Integer> widths) {
        w.append(' ').append(firstGid).append(" [");
        for (int x : widths) {
            w.append(' ').append(x);
        }
        w.append(" ]");
    }

    /** 构造 FontDescriptor 字典：度量按 1000/unitsPerEm 缩放，指向 FontFile2 流。 */
    private String fontDescriptorBody(String baseName, int ff2Obj) {
        int upm = ttf.unitsPerEm();
        int[] b = ttf.bbox();
        int ascent = scaled(ttf.ascent(), upm);
        int descent = scaled(ttf.descent(), upm);
        int cap = ttf.capHeight();
        if (cap == 0) {
            cap = ttf.ascent();
        }
        cap = scaled(cap, upm);
        return "<< /Type /FontDescriptor /FontName /" + baseName + " /Flags 32"
                + " /FontBBox [ " + scaled(b[0], upm) + " " + scaled(b[1], upm)
                + " " + scaled(b[2], upm) + " " + scaled(b[3], upm) + " ]"
                + " /ItalicAngle 0 /Ascent " + ascent + " /Descent " + descent
                + " /CapHeight " + cap + " /StemV 80 /FontFile2 " + ff2Obj + " 0 R >>";
    }

    /** 构造 FontFile2 流对象：原始 TTF 经 FlateDecode 压缩，/Length1 为原始长度。 */
    private byte[] fontFileBody() throws IOException {
        byte[] deflated = deflate(fontBytes);
        String dict = "<< /Length " + deflated.length + " /Length1 " + fontBytes.length + " /Filter /FlateDecode >>";
        return streamObj(dict, deflated);
    }

    /**
     * 构造 ToUnicode CMap 流：建立字形 id -> Unicode 反向映射，
     * 每 100 条 bfchar 一组；补充平面字符以 UTF-16 代理对形式输出。
     */
    private byte[] toUnicodeBody(TreeMap<Integer, Integer> gidToCp) {
        StringBuilder sb = new StringBuilder(256 + gidToCp.size() * 14);
        sb.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n");
        sb.append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n");
        sb.append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n");
        sb.append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n");
        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(gidToCp.entrySet());
        int idx = 0;
        while (idx < entries.size()) {
            int chunk = Math.min(100, entries.size() - idx);
            sb.append(chunk).append(" beginbfchar\n");
            for (int k = 0; k < chunk; k++) {
                Map.Entry<Integer, Integer> e = entries.get(idx + k);
                int gid = e.getKey() & 0xFFFF;
                int cp = e.getValue();
                sb.append(String.format("<%04X> <%s>\n", gid, unicodeHex(cp)));
            }
            sb.append("endbfchar\n");
            idx += chunk;
        }
        sb.append("endcmap\n");
        byte[] data = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
        return streamObj("<< /Length " + data.length + " >>", data);
    }

    /** 将码点转为 ToUnicode bfchar 目标十六进制：BMP 为 4 位，补充平面为代理对 8 位。 */
    private static String unicodeHex(int cp) {
        if (cp <= 0xFFFF) {
            return String.format("%04X", cp);
        }
        byte[] u = Character.toString(cp).getBytes(StandardCharsets.UTF_16BE);
        StringBuilder sb = new StringBuilder(8);
        for (byte b : u) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /** 构造流对象体：{@code dict\nstream\ndata\nendstream}。 */
    private static byte[] streamObj(String dict, byte[] data) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(dict.length() + data.length + 24);
        b.writeBytes(dict.getBytes(StandardCharsets.ISO_8859_1));
        b.writeBytes("\nstream\n".getBytes(StandardCharsets.ISO_8859_1));
        b.writeBytes(data);
        b.writeBytes("\nendstream".getBytes(StandardCharsets.ISO_8859_1));
        return b.toByteArray();
    }

    /** 把所有对象按编号写出并追加 xref 与 trailer，返回完整 PDF 字节数组。 */
    private static byte[] assemblePdf(List<byte[]> objs) {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream(8192);
        writeAscii(pdf, "%PDF-1.4\n");
        pdf.writeBytes(new byte[]{0x25, (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, 0x0A});

        int total = objs.size();
        long[] offsets = new long[total];
        for (int i = 1; i < total; i++) {
            offsets[i] = pdf.size();
            writeAscii(pdf, i + " 0 obj\n");
            pdf.writeBytes(objs.get(i));
            writeAscii(pdf, "\nendobj\n");
        }

        long xrefOffset = pdf.size();
        writeAscii(pdf, "xref\n0 " + total + "\n");
        writeAscii(pdf, "0000000000 65535 f \n");
        for (int i = 1; i < total; i++) {
            writeAscii(pdf, String.format("%010d 00000 n \n", offsets[i]));
        }
        writeAscii(pdf, "trailer\n<< /Size " + total + " /Root 1 0 R >>\nstartxref\n"
                + xrefOffset + "\n%%EOF\n");
        return pdf.toByteArray();
    }

    /** 将字体单位度量按 1000/unitsPerEm 缩放为 PDF 度量并四舍五入取整。 */
    private static int scaled(int fontUnits, int unitsPerEm) {
        return (int) Math.round(fontUnits * (double) PDF_UNITS / unitsPerEm);
    }

    /** 使用 Deflater 压缩字节数组（默认压缩级别）。 */
    private static byte[] deflate(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length / 2);
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION);
        try {
            def.setInput(input);
            def.finish();
            byte[] buf = new byte[8192];
            while (!def.finished()) {
                out.write(buf, 0, def.deflate(buf));
            }
        } finally {
            def.end();
        }
        return out.toByteArray();
    }

    /** 以 ASCII 编码写入字符串到输出流。 */
    private static void writeAscii(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }
}
