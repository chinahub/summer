package cn.jiebaba.summer.web.validation;

import cn.jiebaba.summer.web.bind.HandlerException;

import java.util.List;

public class ValidationException extends HandlerException {
    private final List<ConstraintViolation> violations;

    public ValidationException(List<ConstraintViolation> violations) {
        super("validation failed: " + violations);
        this.violations = violations;
    }

    public List<ConstraintViolation> violations() { return violations; }
}
