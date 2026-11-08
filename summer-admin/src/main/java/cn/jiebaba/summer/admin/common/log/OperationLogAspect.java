package cn.jiebaba.summer.admin.common.log;

import cn.jiebaba.summer.admin.system.log.SysOperLog;
import cn.jiebaba.summer.core.aop.Around;
import cn.jiebaba.summer.core.aop.Aspect;
import cn.jiebaba.summer.core.aop.ProceedingJoinPoint;
import cn.jiebaba.summer.security.core.Authentication;
import cn.jiebaba.summer.security.core.SecurityContextHolder;
import cn.jiebaba.summer.security.userdetails.UserDetails;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 操作日志切面：拦截 admin 模块全部 *Service 方法，但只为显式标注
 * {@link OperLog} 的方法记录日志（未注解方法直接放行）。
 * 记录内容：模块/动作映射的 businessType、方法签名、请求 URL/方式/IP、
 * 当前操作人、脱敏后的请求参数与返回摘要、异常消息、耗时；
 * 成功与业务异常两条路径都会落账，日志写库失败只记服务端日志，
 * 绝不影响业务返回值/异常，也不会递归记录自身。
 */
@Aspect
public class OperationLogAspect {

    private static final Logger LOG = Logger.getLogger(OperationLogAspect.class.getName());

    /** 异常消息落库上限，防止超长堆栈撑爆日志列。 */
    private static final int MAX_ERROR_LEN = 500;

    private final OperLogRecorder recorder;

    public OperationLogAspect(OperLogRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * 环绕通知：仅对显式标注 {@link OperLog} 的方法记录操作日志。
     * 业务异常原样上抛，日志记录本身包在独立 try/catch 中，
     * 任何日志失败都不会替换业务结果。
     *
     * @param jp 被拦截方法的连接点
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的任何异常（含业务异常）
     */
    @Around("execution(* cn.jiebaba.summer.admin..*Service.*(..))")
    public Object around(ProceedingJoinPoint jp) throws Throwable {
        OperLog operLog = resolveOperLog(jp);
        if (operLog == null) {
            return jp.proceed();
        }
        long startNanos = System.nanoTime();
        try {
            Object result = jp.proceed();
            record(jp, operLog, System.nanoTime() - startNanos, result, null);
            return result;
        } catch (Throwable businessFailure) {
            record(jp, operLog, System.nanoTime() - startNanos, null, businessFailure);
            throw businessFailure;
        }
    }

    /**
     * 组装并写入一条操作日志：序列化前递归脱敏、参数/异常摘要限长；
     * 任何失败仅输出服务端日志（不会再次触发操作日志记录）。
     *
     * @param jp            被拦截方法的连接点
     * @param operLog       方法上的 @OperLog 注解
     * @param elapsedNanos  方法耗时（纳秒）
     * @param result        方法返回值（异常路径为 null）
     * @param error         方法抛出的异常（成功路径为 null）
     */
    private void record(ProceedingJoinPoint jp, OperLog operLog, long elapsedNanos,
                        Object result, Throwable error) {
        try {
            SysOperLog entity = new SysOperLog();
            entity.setTitle(operLog.module());
            entity.setBusinessType(businessTypeOf(operLog.action()));
            entity.setMethod(SensitiveDataSanitizer.cap(jp.getSignature(), 200));
            entity.setRequestMethod(AdminRequestContext.method());
            entity.setOperName(currentUsername());
            entity.setDeptName("");
            entity.setOperUrl(SensitiveDataSanitizer.cap(AdminRequestContext.path(), 255));
            entity.setOperIp(SensitiveDataSanitizer.cap(AdminRequestContext.remoteAddress(), 128));
            entity.setOperLocation("");
            entity.setOperParam(SensitiveDataSanitizer.toLogJson(loggableArgs(jp.getArgs())));
            entity.setJsonResult(error == null ? SensitiveDataSanitizer.toLogJson(result) : "");
            entity.setStatus(error == null ? 0 : 1);
            entity.setErrorMsg(error == null ? ""
                    : SensitiveDataSanitizer.cap(String.valueOf(error.getMessage()), MAX_ERROR_LEN));
            entity.setOperTime(LocalDateTime.now());
            entity.setCostTime(TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
            recorder.record(entity);
        } catch (Throwable loggingFailure) {
            LOG.log(Level.WARNING, "操作日志写入失败（已忽略，不影响业务）: " + jp.getSignature(),
                    loggingFailure);
        }
    }

    /**
     * 解析方法上的 @OperLog：优先连接点方法（子类代理路径直接命中），
     * JDK 代理场景回退到目标类实现方法查找。
     *
     * @param jp 被拦截方法的连接点
     * @return 注解实例；未标注返回 null
     */
    private static OperLog resolveOperLog(ProceedingJoinPoint jp) {
        OperLog annotation = jp.getMethod().getAnnotation(OperLog.class);
        if (annotation != null) {
            return annotation;
        }
        Object target = jp.getTarget();
        if (target == null) {
            return null;
        }
        try {
            Method impl = target.getClass().getMethod(
                    jp.getMethod().getName(), jp.getMethod().getParameterTypes());
            return impl.getAnnotation(OperLog.class);
        } catch (NoSuchMethodException notFound) {
            return null;
        }
    }

    /**
     * 过滤可记录的参数：跳过 null、认证主体（{@link Authentication}/{@link UserDetails}，
     * 绝不把密码哈希写进日志）与原始字节数组；集合/Map 参数递归清理其中嵌套的
     * 同类对象，其余参数整体序列化（随后统一脱敏）。
     *
     * @param args 被拦截方法的实参
     * @return 可序列化的参数列表
     */
    private static List<Object> loggableArgs(Object[] args) {
        List<Object> loggable = new ArrayList<>();
        if (args == null) {
            return loggable;
        }
        for (Object arg : args) {
            Object scrubbed = scrub(arg);
            if (scrubbed != Skip.INSTANCE) {
                loggable.add(scrubbed);
            }
        }
        return loggable;
    }

    /** 递归清理单个参数：敏感对象返回 {@link Skip} 标记，集合/Map 逐元素清理。 */
    @SuppressWarnings("unchecked")
    private static Object scrub(Object arg) {
        if (arg == null || arg instanceof Authentication
                || arg instanceof UserDetails || arg instanceof byte[]) {
            return Skip.INSTANCE;
        }
        if (arg instanceof List<?> list) {
            List<Object> cleaned = new ArrayList<>(list.size());
            for (Object item : list) {
                Object scrubbed = scrub(item);
                if (scrubbed != Skip.INSTANCE) {
                    cleaned.add(scrubbed);
                }
            }
            return cleaned;
        }
        if (arg instanceof Map<?, ?> map) {
            Map<Object, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object scrubbed = scrub(entry.getValue());
                if (scrubbed != Skip.INSTANCE) {
                    cleaned.put(entry.getKey(), scrubbed);
                }
            }
            return cleaned;
        }
        return arg;
    }

    /** 跳过标记：清理参数时表示该节点（敏感对象/二进制）不得进入日志。 */
    private enum Skip {
        INSTANCE
    }

    /** 动作到 businessType 的映射（对应 sys_oper_log.business_type 字典）。 */
    private static Integer businessTypeOf(String action) {
        return switch (action == null ? "" : action) {
            case "新增" -> 1;
            case "修改" -> 2;
            case "删除" -> 3;
            case "导出" -> 4;
            case "导入" -> 5;
            case "强退" -> 6;
            case "清空" -> 7;
            default -> 0;
        };
    }

    /** 当前登录名；无认证上下文返回空串。 */
    private static String currentUsername() {
        Authentication authentication = SecurityContextHolder.getAuthentication();
        return authentication == null ? "" : authentication.getName();
    }
}
