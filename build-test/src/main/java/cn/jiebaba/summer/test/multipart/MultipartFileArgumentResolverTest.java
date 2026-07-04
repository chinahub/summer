package cn.jiebaba.summer.test.multipart;

import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;
import cn.jiebaba.summer.web.annotation.RequestPart;
import cn.jiebaba.summer.web.bind.HandlerException;
import cn.jiebaba.summer.web.http.RawHttpRequest;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.multipart.MultipartFile;
import cn.jiebaba.summer.web.multipart.MultipartFileArgumentResolver;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/** End-to-end resolver glue: real multipart HTTP request -> WebRequest -> @RequestPart injection. */
public class MultipartFileArgumentResolverTest {

    public static class Handler {
        public void handle(@RequestPart("file") MultipartFile file,
                           @RequestPart(value = "description", required = false) String description) {}
    }

    private static WebRequest multipartRequest(String body) throws Exception {
        String head = "POST /upload HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: multipart/form-data; boundary=----summerResolverTest\r\n"
                + "Content-Length: " + body.length() + "\r\n\r\n";
        byte[] raw = (head + body).getBytes(StandardCharsets.UTF_8);
        RawHttpRequest parsed = RawHttpRequest.parse(
                new ByteArrayInputStream(raw), 64 * 1024, 10 * 1024 * 1024);
        WebRequest request = new WebRequest(parsed);
        request.remoteAddress("127.0.0.1");
        return request;
    }

    private static String bodyWith(String fileContent, String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("------summerResolverTest\r\n");
        sb.append("Content-Disposition: form-data; name=\"file\"; filename=\"hello.txt\"\r\n");
        sb.append("Content-Type: text/plain\r\n\r\n");
        sb.append(fileContent);
        sb.append("\r\n------summerResolverTest\r\n");
        sb.append("Content-Disposition: form-data; name=\"description\"\r\n\r\n");
        sb.append(description);
        sb.append("\r\n------summerResolverTest--\r\n");
        return sb.toString();
    }

    private static String bodyWithoutFile(String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("------summerResolverTest\r\n");
        sb.append("Content-Disposition: form-data; name=\"description\"\r\n\r\n");
        sb.append(description);
        sb.append("\r\n------summerResolverTest--\r\n");
        return sb.toString();
    }

    @Test
    void resolvesFilePartAndFormField() throws Exception {
        WebRequest request = multipartRequest(bodyWith("hello summer upload", "a text file"));
        MultipartFileArgumentResolver resolver = new MultipartFileArgumentResolver(new Environment());

        Method method = Handler.class.getMethod("handle", MultipartFile.class, String.class);
        Parameter[] params = method.getParameters();
        Type[] generics = method.getGenericParameterTypes();

        MultipartFile file = (MultipartFile) resolver.resolveArgument(
                params[0], generics[0], null, request, null);
        Assert.assertNotNull(file);
        Assert.assertEquals("hello.txt", file.getOriginalFilename());
        Assert.assertEquals("text/plain", file.getContentType());
        Assert.assertEquals("hello summer upload",
                new String(file.getBytes(), StandardCharsets.UTF_8));

        String description = (String) resolver.resolveArgument(
                params[1], generics[1], null, request, null);
        Assert.assertEquals("a text file", description);
    }

    @Test
    void missingRequiredFileThrows() throws Exception {
        WebRequest request = multipartRequest(bodyWithoutFile("only a field"));
        MultipartFileArgumentResolver resolver = new MultipartFileArgumentResolver(new Environment());
        Method method = Handler.class.getMethod("handle", MultipartFile.class, String.class);
        Parameter fileParam = method.getParameters()[0];
        Assert.assertThrows(HandlerException.class,
                () -> resolver.resolveArgument(fileParam, fileParam.getParameterizedType(), null, request, null));
    }

    @Test
    void optionalFieldAbsentIsNull() throws Exception {
        WebRequest request = multipartRequest(bodyWith("bytes", "present"));
        MultipartFileArgumentResolver resolver = new MultipartFileArgumentResolver(new Environment());
        // description present -> should resolve to the value, not throw
        Method method = Handler.class.getMethod("handle", MultipartFile.class, String.class);
        Parameter descParam = method.getParameters()[1];
        String value = (String) resolver.resolveArgument(descParam, descParam.getParameterizedType(), null, request, null);
        Assert.assertEquals("present", value);
    }
}