package cn.jiebaba.summer.office.pdf;

import cn.jiebaba.summer.office.OfficeException;
import cn.jiebaba.summer.office.OfficeReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;

/**
 * PDF 读取器：纯 JDK 实现，从 PDF 提取纯文本，零第三方依赖。
 * <p>采用流扫描策略：遍历所有 {@code stream...endstream} 区段，尝试 zlib 解压（FlateDecode），
 * 在解压后的内容流中解析文本操作符（{@code Tj}、{@code TJ}、{@code Td}、{@code T*}、{@code '}）
 * 提取文本字符串（支持字面量 {@code (...)} 与十六进制 {@code <...>} 两种编码），按行拼接。
 * <p>不依赖 xref/页面对象解析，兼容传统 xref 表与交叉引用流；仅处理包含 {@code BT} 文本块的内容流，
 * 自动跳过字体/图片等非文本流。WinAnsi/Latin-1 编码，CJK 文本可能乱码。
 *
 * <pre>{@code
 * String text = new PdfReader().read(inputStream);
 * }</pre>
 */
public class PdfReader implements OfficeReader {

    @Override
    public String read(InputStream in) throws IOException {
        byte[] data = in.readAllBytes();
        String pdf = new String(data, StandardCharsets.ISO_8859_1);
        StringBuilder result = new StringBuilder();
        int pos = 0;
        while (true) {
            int streamStart = findStreamData(pdf, pos);
            if (streamStart < 0) {
                break;
            }
            int endIdx = pdf.indexOf("endstream", streamStart);
            if (endIdx < 0) {
                break;
            }
            int contentEnd = endIdx;
            while (contentEnd > streamStart && isEol(pdf.charAt(contentEnd - 1))) {
                contentEnd--;
            }
            byte[] raw = Arrays.copyOfRange(data, streamStart, contentEnd);
            byte[] decoded = tryInflate(raw);
            if (decoded == null) {
                decoded = raw;
            }
            String content = new String(decoded, StandardCharsets.ISO_8859_1);
            if (content.contains("BT")) {
                extractText(content, result);
            }
            pos = endIdx + 9;
        }
        return result.toString().trim();
    }

    /** 查找 {@code stream} 关键字后的数据起始位置（跳过 EOL）；未找到返回 -1。 */
    private static int findStreamData(String pdf, int from) {
        int pos = from;
        while (true) {
            int idx = pdf.indexOf("stream", pos);
            if (idx < 0) {
                return -1;
            }
            int after = idx + 6;
            if (after < pdf.length() && pdf.charAt(after) == '\r') {
                after++;
            }
            if (after < pdf.length() && pdf.charAt(after) == '\n') {
                after++;
            } else {
                pos = idx + 6;
                continue;
            }
            return after;
        }
    }

    /** 尝试 zlib 解压数据；非压缩数据返回 null。 */
    private static byte[] tryInflate(byte[] data) {
        if (data.length < 2) {
            return null;
        }
        try {
            Inflater inflater = new Inflater();
            inflater.setInput(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
            byte[] buf = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break;
                    }
                }
                out.write(buf, 0, n);
            }
            inflater.end();
            return out.size() > 0 ? out.toByteArray() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从内容流中提取文本：解析 Tj、TJ、Td、T*、单引号操作符，提取字面量与十六进制字符串。 */
    private static void extractText(String content, StringBuilder out) {
        int i = 0;
        int len = content.length();
        String lastString = null;
        List<String> arrayStrings = new ArrayList<>();
        boolean inArray = false;
        boolean firstTdInBlock = true;

        while (i < len) {
            char c = content.charAt(i);
            if (c == '(') {
                StringBuilder sb = new StringBuilder();
                i = readLiteralString(content, i + 1, sb);
                if (inArray) {
                    arrayStrings.add(sb.toString());
                } else {
                    lastString = sb.toString();
                }
                continue;
            }
            if (c == '<' && i + 1 < len && content.charAt(i + 1) != '<') {
                StringBuilder sb = new StringBuilder();
                i = readHexString(content, i + 1, sb);
                if (inArray) {
                    arrayStrings.add(sb.toString());
                } else {
                    lastString = sb.toString();
                }
                continue;
            }
            if (c == '[') {
                inArray = true;
                arrayStrings.clear();
                i++;
                continue;
            }
            if (c == ']') {
                inArray = false;
                i++;
                continue;
            }
            if (Character.isLetter(c) || c == '*') {
                int start = i;
                while (i < len && (Character.isLetter(content.charAt(i)) || content.charAt(i) == '*')) {
                    i++;
                }
                String op = content.substring(start, i);
                switch (op) {
                    case "Tj" -> {
                        if (lastString != null) {
                            out.append(lastString);
                            lastString = null;
                        }
                    }
                    case "TJ" -> {
                        for (String s : arrayStrings) {
                            out.append(s);
                        }
                        arrayStrings.clear();
                        lastString = null;
                    }
                    case "Td", "TD" -> {
                        if (!firstTdInBlock) {
                            appendNewline(out);
                        }
                        firstTdInBlock = false;
                        lastString = null;
                    }
                    case "T*" -> {
                        appendNewline(out);
                        lastString = null;
                    }
                    case "BT" -> {
                        firstTdInBlock = true;
                        lastString = null;
                    }
                    default -> { }
                }
                continue;
            }
            if (c == '\'') {
                appendNewline(out);
                if (lastString != null) {
                    out.append(lastString);
                    lastString = null;
                }
                i++;
                continue;
            }
            i++;
        }
    }

    /** 读取字面量字符串 {@code (...)}：处理转义序列（\n \r \t \b \f \( \) \\ \ddd）与嵌套括号。 */
    private static int readLiteralString(String content, int start, StringBuilder out) {
        int i = start;
        int len = content.length();
        int depth = 1;
        while (i < len && depth > 0) {
            char c = content.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = content.charAt(i + 1);
                switch (next) {
                    case 'n' -> { out.append('\n'); i += 2; }
                    case 'r' -> { out.append('\r'); i += 2; }
                    case 't' -> { out.append('\t'); i += 2; }
                    case 'b' -> { out.append('\b'); i += 2; }
                    case 'f' -> { out.append('\f'); i += 2; }
                    case '(' -> { out.append('('); i += 2; }
                    case ')' -> { out.append(')'); i += 2; }
                    case '\\' -> { out.append('\\'); i += 2; }
                    case '\n' -> { i += 2; }
                    case '\r' -> { i += 2; if (i < len && content.charAt(i) == '\n') i++; }
                    default -> {
                        if (Character.isDigit(next)) {
                            int val = next - '0';
                            i += 2;
                            for (int k = 0; k < 2 && i < len && Character.isDigit(content.charAt(i)); k++) {
                                val = val * 8 + (content.charAt(i) - '0');
                                i++;
                            }
                            out.append((char) (val & 0xFF));
                        } else {
                            out.append(next);
                            i += 2;
                        }
                    }
                }
            } else if (c == '(') {
                depth++;
                out.append(c);
                i++;
            } else if (c == ')') {
                depth--;
                if (depth > 0) {
                    out.append(c);
                }
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return i;
    }

    /** 读取十六进制字符串 {@code <...>}：每两位十六进制解码为一个字节字符。 */
    private static int readHexString(String content, int start, StringBuilder out) {
        int i = start;
        int len = content.length();
        StringBuilder hex = new StringBuilder();
        while (i < len && content.charAt(i) != '>') {
            char c = content.charAt(i);
            if (isHexDigit(c)) {
                hex.append(c);
            }
            i++;
        }
        if (i < len) {
            i++;
        }
        for (int j = 0; j + 1 < hex.length(); j += 2) {
            out.append((char) Integer.parseInt(hex.substring(j, j + 2), 16));
        }
        if (hex.length() % 2 == 1) {
            out.append((char) Integer.parseInt(hex.substring(hex.length() - 1) + "0", 16));
        }
        return i;
    }

    /** 追加换行符（避免连续换行）。 */
    private static void appendNewline(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isEol(char c) {
        return c == '\n' || c == '\r';
    }
}
