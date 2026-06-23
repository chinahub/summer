package cn.jiebaba.summer.web.server;

import cn.jiebaba.summer.core.scanner.AnnotationUtils;
import cn.jiebaba.summer.web.annotation.ResponseBody;
import cn.jiebaba.summer.web.annotation.ResponseStatus;
import cn.jiebaba.summer.web.bind.HandlerException;
import cn.jiebaba.summer.web.bind.HandlerMethodInvoker;
import cn.jiebaba.summer.web.convert.MessageConverter;
import cn.jiebaba.summer.web.http.HttpMethod;
import cn.jiebaba.summer.web.http.HttpStatus;
import cn.jiebaba.summer.web.validation.ValidationException;
import cn.jiebaba.summer.web.http.MediaType;
import cn.jiebaba.summer.web.http.WebRequest;
import cn.jiebaba.summer.web.http.WebResponse;
import cn.jiebaba.summer.web.json.Json;
import cn.jiebaba.summer.web.routing.RouteMatch;
import cn.jiebaba.summer.web.routing.Router;
import cn.jiebaba.summer.web.support.ExceptionHandlerRegistry;

import java.util.LinkedHashMap;
import java.util.concurrent.CompletionStage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RequestDispatcher {
    private static final Logger LOG = Logger.getLogger(RequestDispatcher.class.getName());

    private final Router router;
    private final HandlerMethodInvoker invoker;
    private final MessageConverter converter;
    private final ExceptionHandlerRegistry exceptions;
    private final String contextPath;

    public RequestDispatcher(Router router, HandlerMethodInvoker invoker, MessageConverter converter,
                             ExceptionHandlerRegistry exceptions, String contextPath) {
        this.router = router;
        this.invoker = invoker;
        this.converter = converter;
        this.exceptions = exceptions;
        this.contextPath = contextPath == null ? "" : contextPath;
    }

    public void dispatch(WebRequest request, WebResponse response) {
        try {
            String path = stripContext(request.path());
            Optional<RouteMatch> match = router.match(request.method(), path);
            if (match.isEmpty()) {
                writeNoRoute(response, request.method(), path);
                return;
            }
            RouteMatch route = match.get();
            route.pathVariables().forEach(request::putPathVariable);
            Object result = invoker.invoke(route, request, response);
            writeResult(route, result, response);
        } catch (Throwable t) {
            handleException(t, request, response);
        }
    }

    private String stripContext(String path) {
        if (contextPath.isEmpty()) return path;
        if (path.equals(contextPath)) return "/";
        if (path.startsWith(contextPath + "/")) return path.substring(contextPath.length());
        return path;
    }

    private void writeResult(RouteMatch route, Object result, WebResponse response) {
        ResponseStatus status = route.mapping().handlerMethod().getAnnotation(ResponseStatus.class);
        if (status != null && status.value() != 200) response.status(status.value());
        boolean responseBody = AnnotationUtils.hasAnnotation(route.mapping().handlerMethod(), ResponseBody.class)
                || AnnotationUtils.hasAnnotation(route.mapping().handlerBean().getClass(), ResponseBody.class);
        if (result == null) {
            if (response.status() == HttpStatus.OK.code()) response.status(HttpStatus.NO_CONTENT.code());
            return;
        }
        if (result instanceof CompletionStage<?> cs) {
            Object resolved;
            try {
                resolved = cs.toCompletableFuture().join();
            } catch (java.util.concurrent.CompletionException ce) {
                Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
                if (cause instanceof RuntimeException re) throw re;
                if (cause instanceof Error err) throw err;
                throw new RuntimeException(cause);
            }
            writeResult(route, resolved, response);
            return;
        }
        if (result instanceof WebResponse) {
            return;
        }
        if (responseBody) {
            response.contentType(converter.defaultContentType());
            response.body(converter.write(result, response.header("Accept")));
        } else if (result instanceof String s) {
            response.contentType(MediaType.TEXT_PLAIN_UTF8);
            response.body(s);
        } else if (result instanceof byte[] bytes) {
            response.contentType(MediaType.APPLICATION_OCTET_STREAM);
            response.body(bytes);
        } else {
            response.contentType(converter.defaultContentType());
            response.body(converter.write(result, response.header("Accept")));
        }
    }

    private void writeNoRoute(WebResponse response, HttpMethod method, String path) {
        boolean pathExists = router.routes().stream()
                .anyMatch(r -> r.pattern().match(path).isPresent());
        int status = pathExists ? HttpStatus.METHOD_NOT_ALLOWED.code() : HttpStatus.NOT_FOUND.code();
        response.status(status);
        response.contentType(MediaType.APPLICATION_JSON_UTF8);
        response.body(errorBody(status, path, pathExists ? "Method Not Allowed" : "Not Found", method + " " + path));
    }

    private void handleException(Throwable t, WebRequest request, WebResponse response) {
        if (t instanceof ValidationException ve) {
            response.status(HttpStatus.BAD_REQUEST.code());
            response.contentType(MediaType.APPLICATION_JSON_UTF8);
            response.body(validationBody(ve));
            return;
        }
        if (!exceptions.isEmpty()) {
            var entry = exceptions.resolve(t);
            if (entry != null) {
                try {
                    Object result = exceptions.invoke(entry, t, request, response);
                    if (result == null) {
                        if (response.status() == HttpStatus.OK.code()) response.status(HttpStatus.INTERNAL_SERVER_ERROR.code());
                    } else {
                        response.contentType(converter.defaultContentType());
                        response.body(converter.write(result, response.header("Accept")));
                    }
                    return;
                } catch (HandlerException he) {
                    t = he;
                }
            }
        }
        int status = (t instanceof HandlerException) ? HttpStatus.BAD_REQUEST.code() : HttpStatus.INTERNAL_SERVER_ERROR.code();
        response.status(status);
        response.contentType(MediaType.APPLICATION_JSON_UTF8);
        LOG.log(Level.WARNING, "Unhandled exception for " + request.method() + " " + request.path(), t);
        response.body(errorBody(status, request.path(), reason(status), messageOf(t)));
    }

    private static String reason(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Error";
        };
    }

    private static String messageOf(Throwable t) {
        String msg = t.getMessage();
        return msg == null ? t.getClass().getSimpleName() : msg;
    }

    private String validationBody(ValidationException ve) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", java.time.Instant.now().toString());
        body.put("status", HttpStatus.BAD_REQUEST.code());
        body.put("error", "Bad Request");
        List<String> fields = new java.util.ArrayList<>();
        for (var v : ve.violations()) fields.add(v.field() + ": " + v.message());
        body.put("violations", fields);
        return Json.stringify(body);
    }

    private String errorBody(int status, String path, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", java.time.Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);
        return Json.stringify(body);
    }
}
