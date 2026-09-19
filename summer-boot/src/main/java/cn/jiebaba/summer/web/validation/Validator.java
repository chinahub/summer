package cn.jiebaba.summer.web.validation;

import cn.jiebaba.summer.core.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 依据 summer 约束注解校验对象字段。
 * 支持 bean 类型字段上嵌套的 {@link Valid @Valid}（仅一层）。
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

    /**
     * 递归校验 Bean 的各字段：依次检查 NotNull/NotBlank/NotEmpty，再对非 null 值校验
     * Size/Min/Max/Pattern/Email，并对 bean 类型字段上嵌套的 {@link Valid} 递归校验。
     *
     * @param prefix     字段路径前缀
     * @param bean        待校验 Bean
     * @param violations  收集校验违规的列表
     */
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
                // 其余约束（Size/Min/Max/Pattern/Email）要求值非 null
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
        // 始终用第一个参数（字段值）替换 {value}
        if (a != null) result = result.replace("{value}", String.valueOf(a));
        // 替换 size/min/max 约束的 {min} 与 {max}
        if (a != null) result = result.replace("{min}", String.valueOf(a));
        if (b != null) result = result.replace("{max}", String.valueOf(b));
        // 仅对 pattern 约束替换 {regexp}（作为第一个参数传入）
        if (a != null) result = result.replace("{regexp}", String.valueOf(a));
        return result;
    }

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
}
