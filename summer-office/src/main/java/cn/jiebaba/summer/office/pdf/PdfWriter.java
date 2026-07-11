package cn.jiebaba.summer.office.pdf;

import cn.jiebaba.summer.office.OfficeWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PDF 写入器：纯 JDK 实现，将文本内容生成为 PDF 字节数组，零第三方依赖。
 * <p>直接拼装 PDF 对象结构（Catalog / Pages / Page / Font / Content Stream），
 * 文本按换行拆分，每行一个 Tj 文本操作符，自动分页（A4 纵向，Helvetica 12pt）。
 * <p><b>限制</b>：使用标准 Type1 字体 Helvetica，仅支持 Latin/WinAnsiEncoding（ASCII + 拉丁补充），
 * 中文等 CJK 字符替换为 {@code ?}；无自动水平换行（超长行溢出页宽）。
 *
 * <pre>{@code
 * byte[] pdf = new PdfWriter().write("Hello\nWorld");
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

    @Override
    public byte[] write(String content) throws IOException {
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

            byte[] stream = buildContentStream(pages.get(p)).getBytes(StandardCharsets.ISO_8859_1);
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

    /** 将所有行按每页行数切分为多页。 */
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

    /** 构建单页内容流：BT 块内逐行 Td 定位 + Tj 显示文本。 */
    private static String buildContentStream(List<String> lines) {
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

    /** 以 ASCII 编码写入字符串到输出流。 */
    private static void writeAscii(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }
}
