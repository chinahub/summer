import java.lang.reflect.Method;
import io.summer.web.annotation.PostMapping;
import io.summer.web.annotation.ResponseBody;

public class MethodDebug {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = io.summer.sample.controller.HelloController.class;
        System.out.println("=== All methods of " + clazz.getName() + " ===");
        for (Method m : clazz.getMethods()) {
            if (m.getDeclaringClass() == Object.class) continue;
            PostMapping pm = m.getAnnotation(PostMapping.class);
            System.out.println("  " + m.getName() + " - PostMapping=" + (pm != null)
                + (pm != null ? " value=" + java.util.Arrays.toString(pm.value()) : ""));
        }
        // Specifically check say method
        System.out.println("\n=== Checking say method ===");
        try {
            Method say = clazz.getMethod("say", java.util.Map.class);
            System.out.println("  Found: " + say);
            System.out.println("  PostMapping: " + say.getAnnotation(PostMapping.class));
            System.out.println("  ResponseBody: " + say.getAnnotation(ResponseBody.class));
            PostMapping pm = say.getAnnotation(PostMapping.class);
            if (pm != null) {
                System.out.println("  value: " + java.util.Arrays.toString(pm.value()));
                System.out.println("  path: " + java.util.Arrays.toString(pm.path()));
            }
        } catch (NoSuchMethodException e) {
            System.out.println("  say method NOT FOUND: " + e.getMessage());
        }
    }
}