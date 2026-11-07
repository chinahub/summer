package cn.jiebaba.summer.admin.common.validation;

import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.web.validation.ConstraintViolation;
import cn.jiebaba.summer.web.validation.Validator;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 请求显式校验入口：后续控制器统一在方法体内调用 {@link #requireValid(Object)}
 * 做参数校验，而不是在参数上使用会触发框架内置错误格式的 {@code @Valid}；
 * 校验失败统一抛 {@link BusinessException}(400)。
 */
public final class RequestValidator {

    private RequestValidator() {
    }

    /**
     * 校验目标对象字段约束，全部违规字段消息拼接为一条提示。
     *
     * @param target 待校验对象（null 视为通过）
     * @param <T>    对象类型
     * @return 原对象，便于链式使用
     * @throws BusinessException 校验失败时抛出，状态码 400
     */
    public static <T> T requireValid(T target) {
        List<ConstraintViolation> violations = Validator.validate(target);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(v -> v.field() + ": " + v.message())
                    .collect(Collectors.joining("; "));
            throw new BusinessException(400, msg);
        }
        return target;
    }
}
