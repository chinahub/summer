package cn.jiebaba.summer.web.multipart;

import cn.jiebaba.summer.web.bind.HandlerException;

import java.nio.charset.StandardCharsets;

/**
 * 仅基于 JDK 实现的极简 RFC 2388 {@code multipart/form-data} 解析器。
 * 作用于已缓冲的请求体（请求体大小受服务端 {@code server.max-request-size} 限制）。
 * 不做流式处理，也不写临时文件。
 */
public final class MultipartParser {

    private static final byte CR = '\r';
    private static final byte LF = '\n';
    private static final byte[] HEADER_TERMINATOR = {CR, LF, CR, LF};

    private MultipartParser() {}

    /**
     * 解析 multipart/form-data 请求体：依据 Content-Type 中的边界切分各部分，
     * 逐个解析其头部与内容并交给 {@link #processPart} 处理。
     *
     * @param contentType  Content-Type，需包含 boundary
     * @param body         已缓冲的请求体
     * @param maxFileSize  单个文件最大字节数
     * @return 解析得到的表单数据
     */
    public static MultipartForm parse(String contentType, byte[] body, long maxFileSize) {
        String boundary = boundaryOf(contentType);
        if (boundary == null || boundary.isEmpty()) {
            throw new HandlerException("Content-Type is not multipart/form-data with a boundary");
        }
        byte[] delim = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] data = body == null ? new byte[0] : body;

        MultipartForm form = new MultipartForm();
        int pos = indexOf(data, delim, 0);
        if (pos < 0) {
            throw new HandlerException("multipart body missing initial boundary");
        }
        pos += delim.length;

        while (pos < data.length) {
            // 结束标记 "--" 终止请求体
            if (pos + 1 < data.length && data[pos] == '-' && data[pos + 1] == '-') {
                break;
            }
            pos = skipCrlf(data, pos);
            int headerEnd = indexOf(data, HEADER_TERMINATOR, pos);
            if (headerEnd < 0) {
                throw new HandlerException("malformed multipart part: missing header terminator");
            }
            String headerBlock = new String(data, pos, headerEnd - pos, StandardCharsets.UTF_8);
            int contentStart = headerEnd + HEADER_TERMINATOR.length;
            int nextDelim = indexOf(data, delim, contentStart);
            if (nextDelim < 0) {
                throw new HandlerException("malformed multipart part: missing closing boundary");
            }
            int contentEnd = nextDelim;
            if (contentEnd >= 2 && data[contentEnd - 2] == CR && data[contentEnd - 1] == LF) {
                contentEnd -= 2;
            }
            byte[] content = subarray(data, contentStart, contentEnd);
            processPart(form, headerBlock, content, maxFileSize);
            pos = nextDelim + delim.length;
        }
        return form;
    }

    /**
     * 处理单个部分：解析 Content-Disposition 中的 name/filename 与 Content-Type，
     * 带文件名则作为文件（校验大小）加入表单，否则作为普通字段加入。
     */
    private static void processPart(MultipartForm form, String headerBlock, byte[] content, long maxFileSize) {
        String name = null, filename = null, contentType = null;
        for (String line : headerBlock.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String headerName = line.substring(0, colon).trim().toLowerCase();
            String headerValue = line.substring(colon + 1).trim();
            if (headerName.equals("content-disposition")) {
                name = param(headerValue, "name");
                filename = param(headerValue, "filename");
            } else if (headerName.equals("content-type")) {
                contentType = headerValue;
            }
        }
        if (name == null) return; // 无名部分，跳过
        if (filename != null) {
            if (maxFileSize > 0 && content.length > maxFileSize) {
                throw new HandlerException("Uploaded part '" + name + "' size " + content.length
                        + " exceeds max-file-size " + maxFileSize);
            }
            form.addFile(name, new SummerMultipartFile(name, cleanFilename(filename), contentType, content));
        } else {
            form.addField(name, new String(content, StandardCharsets.UTF_8));
        }
    }

    static String boundaryOf(String contentType) {
        if (contentType == null) return null;
        for (String part : contentType.split(";")) {
            String token = part.trim();
            if (token.regionMatches(true, 0, "boundary=", 0, "boundary=".length())) {
                String value = token.substring("boundary=".length());
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static String param(String headerValue, String key) {
        String prefix = key + "=";
        for (String token : headerValue.split(";")) {
            String t = token.trim();
            if (t.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String value = t.substring(prefix.length());
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static String cleanFilename(String filename) {
        if (filename == null || filename.isEmpty()) return filename;
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return slash < 0 ? filename : filename.substring(slash + 1);
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        if (pattern.length == 0) return from;
        for (int i = from; i + pattern.length <= data.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) { match = false; break; }
            }
            if (match) return i;
        }
        return -1;
    }

    private static int skipCrlf(byte[] data, int pos) {
        if (pos + 1 < data.length && data[pos] == CR && data[pos + 1] == LF) return pos + 2;
        return pos;
    }

    private static byte[] subarray(byte[] data, int from, int to) {
        int length = Math.max(0, to - from);
        byte[] out = new byte[length];
        System.arraycopy(data, from, out, 0, length);
        return out;
    }
}
