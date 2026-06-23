package cn.jiebaba.summer.core.scanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public final class AnnotationUtils {
    private AnnotationUtils() {}

    /** Find an annotation on the element, walking meta-annotations recursively. */
    public static <A extends Annotation> A findAnnotation(AnnotatedElement element, Class<A> annotationType) {
        if (element == null || annotationType == null) return null;
        Set<String> visited = new HashSet<>();
        Deque<AnnotatedElement> stack = new ArrayDeque<>();
        stack.push(element);
        while (!stack.isEmpty()) {
            AnnotatedElement current = stack.pop();
            if (current == null) continue;
            for (Annotation ann : current.getAnnotations()) {
                Class<? extends Annotation> type = ann.annotationType();
                if (type.equals(annotationType)) {
                    return annotationType.cast(ann);
                }
                if (visited.add(type.getName())) {
                    stack.push(type);
                }
            }
        }
        return null;
    }

    public static boolean hasAnnotation(AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return findAnnotation(element, annotationType) != null;
    }
}
