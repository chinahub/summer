package cn.jiebaba.summer.web.multipart;

import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.web.annotation.RequestPart;
import cn.jiebaba.summer.web.bind.HandlerException;
import cn.jiebaba.summer.web.bind.HandlerMethodArgumentResolver;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.routing.RouteMatch;

import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves {@code MultipartFile} / {@code MultipartFile[]} / {@code List<MultipartFile>}
 * parameters and {@code @RequestPart}-annotated form fields against the parsed
 * multipart body. The body is parsed lazily and cached on the request. Collected
 * automatically by {@code HandlerMethodInvoker} as a context bean.
 */
public final class MultipartFileArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String FORM_ATTR = "summer.web.multipart.form";
    private static final long DEFAULT_MAX_FILE_SIZE = 1024L * 1024L;

    private final long maxFileSize;

    public MultipartFileArgumentResolver(Environment env) {
        this.maxFileSize = parseSize(env.getProperty("summer.web.multipart.max-file-size"), DEFAULT_MAX_FILE_SIZE);
    }

    @Override
    public boolean supportsParameter(Parameter parameter) {
        if (parameter.isAnnotationPresent(RequestPart.class)) return true;
        Class<?> type = parameter.getType();
        if (type == MultipartFile.class || type == MultipartFile[].class) return true;
        if (type == List.class) {
            Type generic = parameter.getParameterizedType();
            if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0
                    && pt.getActualTypeArguments()[0] instanceof Class<?> c && c == MultipartFile.class) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object resolveArgument(Parameter parameter, Type genericType, RouteMatch match,
                                  WebRequest request, WebResponse response) throws Exception {
        MultipartForm form = form(request);
        String name = partName(parameter);
        boolean required = required(parameter);
        Class<?> type = parameter.getType();

        if (type == MultipartFile.class) {
            MultipartFile file = form.getFile(name);
            if (file == null && required) throw missing(name);
            return file;
        }
        if (type == MultipartFile[].class) {
            return form.getFiles(name).toArray(MultipartFile[]::new);
        }
        if (type == List.class) {
            return new ArrayList<>(form.getFiles(name));
        }
        // @RequestPart on a simple type -> regular form field
        String value = form.getField(name);
        if (value == null && required) throw missing(name);
        return value;
    }

    private MultipartForm form(WebRequest request) {
        Object cached = request.getAttribute(FORM_ATTR);
        if (cached instanceof MultipartForm mf) return mf;
        String contentType = request.contentType();
        MultipartForm mf;
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
            mf = MultipartParser.parse(contentType, request.bodyBytes(), maxFileSize);
        } else {
            mf = new MultipartForm();
        }
        request.setAttribute(FORM_ATTR, mf);
        return mf;
    }

    private static String partName(Parameter parameter) {
        RequestPart rp = parameter.getAnnotation(RequestPart.class);
        if (rp != null) {
            if (!rp.value().isEmpty()) return rp.value();
            if (!rp.name().isEmpty()) return rp.name();
        }
        return parameter.getName();
    }

    private static boolean required(Parameter parameter) {
        RequestPart rp = parameter.getAnnotation(RequestPart.class);
        return rp == null || rp.required();
    }

    private static HandlerException missing(String name) {
        return new HandlerException("Missing required multipart part: " + name);
    }

    /** Parse a size string such as {@code 1MB}, {@code 512KB}, {@code 1048576} into bytes. */
    private static long parseSize(String text, long fallback) {
        if (text == null || text.isBlank()) return fallback;
        String s = text.trim().toUpperCase();
        long multiplier = 1L;
        if (s.endsWith("KB")) { multiplier = 1024L; s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("MB")) { multiplier = 1024L * 1024; s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("GB")) { multiplier = 1024L * 1024 * 1024; s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("B")) { s = s.substring(0, s.length() - 1); }
        try {
            return Long.parseLong(s.trim()) * multiplier;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}