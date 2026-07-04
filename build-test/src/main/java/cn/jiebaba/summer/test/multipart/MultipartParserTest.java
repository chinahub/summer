package cn.jiebaba.summer.test.multipart;

import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.web.multipart.MultipartFile;
import cn.jiebaba.summer.web.multipart.MultipartForm;
import cn.jiebaba.summer.web.multipart.MultipartParser;

import java.nio.charset.StandardCharsets;

public class MultipartParserTest {

    private static byte[] body(String boundary, String filePart, String fieldPart) {
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"hello.txt\"\r\n");
        sb.append("Content-Type: text/plain\r\n\r\n");
        sb.append(filePart);
        sb.append("\r\n--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"description\"\r\n\r\n");
        sb.append(fieldPart);
        sb.append("\r\n--").append(boundary).append("--\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesFilePartAndFormField() {
        String boundary = "----summerTestBoundary";
        byte[] body = body(boundary, "hello summer upload", "a text file");
        MultipartForm form = MultipartParser.parse(
                "multipart/form-data; boundary=" + boundary, body, 1024 * 1024);

        MultipartFile file = form.getFile("file");
        Assert.assertNotNull(file);
        Assert.assertEquals("file", file.getName());
        Assert.assertEquals("hello.txt", file.getOriginalFilename());
        Assert.assertEquals("text/plain", file.getContentType());
        Assert.assertEquals("hello summer upload".length(), file.getSize());
        Assert.assertEquals("hello summer upload",
                new String(file.getBytes(), StandardCharsets.UTF_8));
        Assert.assertFalse(file.isEmpty());
        Assert.assertEquals("a text file", form.getField("description"));
    }

    @Test
    void multipleFilesShareFieldName() {
        String boundary = "b";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"files\"; filename=\"f").append(i).append("\"\r\n\r\n");
            sb.append("c").append(i);
            sb.append("\r\n");
        }
        sb.append("--").append(boundary).append("--\r\n");
        MultipartForm form = MultipartParser.parse("multipart/form-data; boundary=" + boundary,
                sb.toString().getBytes(StandardCharsets.UTF_8), 1024 * 1024);
        Assert.assertEquals(2, form.getFiles("files").size());
        Assert.assertEquals("f0", form.getFiles("files").get(0).getOriginalFilename());
        Assert.assertEquals("f1", form.getFiles("files").get(1).getOriginalFilename());
    }

    @Test
    void rejectsFileLargerThanMaxSize() {
        String boundary = "b";
        byte[] body = body(boundary, "0123456789", "x");
        Assert.assertThrows(RuntimeException.class,
                () -> MultipartParser.parse("multipart/form-data; boundary=" + boundary, body, 5));
    }

    @Test
    void stripsPathFromFilename() {
        String boundary = "b";
        byte[] body = body(boundary, "x", "y");
        // rebuild with a malicious filename
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"../../etc/passwd\"\r\n\r\n");
        sb.append("x");
        sb.append("\r\n--").append(boundary).append("--\r\n");
        MultipartForm form = MultipartParser.parse("multipart/form-data; boundary=" + boundary,
                sb.toString().getBytes(StandardCharsets.UTF_8), 1024 * 1024);
        Assert.assertEquals("passwd", form.getFile("file").getOriginalFilename());
    }

    @Test
    void rejectsMissingBoundary() {
        Assert.assertThrows(RuntimeException.class,
                () -> MultipartParser.parse("application/json", new byte[0], 1024));
    }
}