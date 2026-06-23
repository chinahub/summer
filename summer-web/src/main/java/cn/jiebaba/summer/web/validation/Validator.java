package cn.jiebaba.summer.web.validation;

import cn.jiebaba.summer.core.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Validates an object's fields against the summer constraint annotations.
 * Supports nested {@link Valid @Valid} on bean-typed fields (one level deep).
 */
public final class Validator {

    private Validator() {}

    public static List<ConstraintViolation> validate(Object target) {
        List<ConstraintViolation> violations = new ArrayList<>();
        if (target == null) return violations;
        validateBean("", target, violations);
        return violations;
    }

    public static void requireValid(Object target) {
        List<ConstraintViolation> violations = validate(target);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }
    }

    private static void validateBean(String prefix, Object bean, List<ConstraintViolation> violations) {
        for (Field field : ReflectionUtils.collectFields(bean.getClass())) {
            field.setAccessible(true);
            Object value;
            try { value = field.get(bean); } catch (IllegalAccessException e) { continue; }
            String path = prefix.isEmpty() ? field.getName() : prefix + "." + field.getName();

            if (field.isAnnotationPresent(NotNull.class) && value == null) {
                violations.add(new ConstraintViolation(path, null, field.getAnnotation(NotNull.class).message()));
            }
            if (field.isAnnotationPresent(NotBlank.class) && (value == null || isBlank(value))) {
                violations.add(new ConstraintViolation(path, value, field.getAnnotation(NotBlank.class).message()));
            }
            if (field.isAnnotationPresent(NotEmpty.class) && (value == null || isEmpty(value))) {
                violations.add(new ConstraintViolation(path, value, field.getAnnotation(NotEmpty.class).message()));
            }
            if (value == null) {
                // remaining constraints (Size/Min/Max/Pattern/Email) require a non-null value
                continue;
            }
            if (field.isAnnotationPresent(Size.class)) {
                Size size = field.getAnnotation(Size.class);
                int length = lengthOf(value);
                if (length < size.min() || length > size.max()) {
                    violations.add(new ConstraintViolation(path, value, format(size.message(), size.min(), size.max())));
                }
            }
            if (field.isAnnotationPresent(Min.class) && value instanceof Number n && n.longValue() < field.getAnnotation(Min.class).value()) {
                Min min = field.getAnnotation(Min.class);
                violations.add(new ConstraintViolation(path, value, format(min.message(), min.value(), null)));
            }
            if (field.isAnnotationPresent(Max.class) && value instanceof Number n && n.longValue() > field.getAnnotation(Max.class).value()) {
                Max max = field.getAnnotation(Max.class);
                violations.add(new ConstraintViolation(path, value, format(max.message(), max.value(), null)));
            }
            if (field.isAnnotationPresent(Pattern.class) && value instanceof CharSequence cs) {
                Pattern p = field.getAnnotation(Pattern.class);
                if (!java.util.regex.Pattern.matches(p.regexp(), cs.toString())) {
                    violations.add(new ConstraintViolation(path, value, format(p.message(), p.regexp(), null)));
                }
            }
            if (field.isAnnotationPresent(Email.class) && value instanceof CharSequence cs) {
                if (!EMAIL_PATTERN.matcher(cs.toString()).matches()) {
                    violations.add(new ConstraintViolation(path, value, field.getAnnotation(Email.class).message()));
                }
            }
            if (field.isAnnotationPresent(Valid.class) && !field.getType().getName().startsWith("java.")) {
                validateBean(path, value, violations);
            }
        }
    }

    private static boolean isBlank(Object value) {
        return value instanceof CharSequence cs && cs.toString().trim().isEmpty();
    }

    private static boolean isEmpty(Object value) {
        if (value instanceof CharSequence cs) return cs.isEmpty();
        if (value instanceof Collection<?> c) return c.isEmpty();
        if (value instanceof Map<?, ?> m) return m.isEmpty();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value) == 0;
        return false;
    }

    private static int lengthOf(Object value) {
        if (value instanceof CharSequence cs) return cs.length();
        if (value instanceof Collection<?> c) return c.size();
        if (value instanceof Map<?, ?> m) return m.size();
        if (value.getClass().isArray()) return java.lang.reflect.Array.getLength(value);
        return 0;
    }

    private static String format(String message, Object a, Object b) {
        String result = message;
        // Always replace {value} with the first argument (the field value)
        if (a != null) result = result.replace("{value}", String.valueOf(a));
        // Replace {min} and {max} for size/min/max constraints
        if (a != null) result = result.replace("{min}", String.valueOf(a));
        if (b != null) result = result.replace("{max}", String.valueOf(b));
        // Replace {regexp} only for pattern constraints (passed as first argument)
        if (a != null) result = result.replace("{regexp}", String.valueOf(a));
        return result;
    }

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
}
