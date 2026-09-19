package cn.jiebaba.summer.web.validation;

public record ConstraintViolation(String field, Object invalidValue, String message) {
    @Override public String toString() { return field + ": " + message; }
}
