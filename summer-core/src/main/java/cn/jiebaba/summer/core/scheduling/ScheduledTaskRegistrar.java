package cn.jiebaba.summer.core.scheduling;

import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovers {@link Scheduled @Scheduled} methods on context beans and runs each
 * on its own virtual thread. Every task owns a self-scheduling loop, so one
 * task's blocking body never delays another's timing (virtual threads unmount
 * while blocked) and {@code fixedDelay} is measured from the real end of a run
 * to the start of the next, guaranteeing executions never overlap.
 */
public final class ScheduledTaskRegistrar {

    private static final Logger LOG = Logger.getLogger(ScheduledTaskRegistrar.class.getName());

    private final List<Thread> tasks = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    public int scheduleAll(ApplicationContext context) {
        int count = 0;
        for (String name : context.getBeanNamesForType(Object.class)) {
            Class<?> type = context.getType(name);
            if (type == null) continue;
            List<Method> methods = collectScheduledMethods(type);
            if (methods.isEmpty()) continue;
            Object bean;
            try {
                bean = context.getBean(name);
            } catch (Exception e) {
                continue;
            }
            if (bean == null) continue;
            for (Method method : methods) {
                for (Scheduled s : method.getAnnotationsByType(Scheduled.class)) {
                    try {
                        startTask(bean, method, s);
                        count++;
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Skipping @Scheduled " + method + ": " + e.getMessage(), e);
                    }
                }
            }
        }
        if (count > 0) {
            LOG.info("summer scheduling: registered " + count + " scheduled task(s)");
        }
        return count;
    }

    private void startTask(Object bean, Method method, Scheduled s) {
        if (method.getParameterCount() != 0) {
            throw new IllegalArgumentException("@Scheduled method must have no parameters");
        }
        Method invokable = resolveInvokable(bean, method);
        if (invokable == null) {
            throw new IllegalArgumentException("@Scheduled " + method
                    + " is not invokable on bean " + bean.getClass().getName()
                    + " (a JDK proxy only exposes interface methods)");
        }
        ReflectionUtils.makeAccessible(invokable);
        Runnable task = () -> {
            try {
                invokable.invoke(bean);
            } catch (Throwable t) {
                Throwable cause = (t instanceof InvocationTargetException ite) ? ite.getCause() : t;
                if (!running && cause instanceof InterruptedException) return;
                LOG.log(Level.WARNING, "Scheduled task " + method + " failed", cause);
            }
        };

        long initialDelay = Math.max(0, s.initialDelay());
        String label = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        Thread thread;
        if (!s.cron().isBlank()) {
            CronExpression expr = new CronExpression(s.cron());
            validateFires(expr);
            thread = Thread.ofVirtual().name("summer-scheduled-" + label)
                    .start(() -> runCron(task, expr, initialDelay));
        } else if (s.fixedRate() > 0) {
            long rate = s.fixedRate();
            thread = Thread.ofVirtual().name("summer-scheduled-" + label)
                    .start(() -> runFixedRate(task, initialDelay, rate));
        } else if (s.fixedDelay() > 0) {
            long delay = s.fixedDelay();
            thread = Thread.ofVirtual().name("summer-scheduled-" + label)
                    .start(() -> runFixedDelay(task, initialDelay, delay));
        } else {
            throw new IllegalArgumentException("no cron/fixedRate/fixedDelay configured");
        }
        tasks.add(thread);
    }

    private void runFixedDelay(Runnable task, long initialDelay, long fixedDelay) {
        try {
            if (initialDelay > 0) Thread.sleep(initialDelay);
            while (running) {
                task.run();
                if (!running) break;
                Thread.sleep(fixedDelay);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runFixedRate(Runnable task, long initialDelay, long fixedRate) {
        try {
            if (initialDelay > 0) Thread.sleep(initialDelay);
            long nextStart = System.currentTimeMillis();
            while (running) {
                long delay = nextStart - System.currentTimeMillis();
                if (delay > 0) Thread.sleep(delay);
                if (!running) break;
                task.run();
                nextStart += fixedRate;
                if (nextStart <= System.currentTimeMillis()) {
                    nextStart = System.currentTimeMillis() + fixedRate;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runCron(Runnable task, CronExpression expr, long initialDelay) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime earliest = initialDelay > 0 ? now.plusNanos(initialDelay * 1_000_000L) : now;
            LocalDateTime next = expr.nextFire(now);
            while (next.isBefore(earliest)) {
                next = expr.nextFire(next);
            }
            while (running) {
                long delayMs = Duration.between(LocalDateTime.now(), next).toMillis();
                if (delayMs > 0) Thread.sleep(delayMs);
                if (!running) break;
                task.run();
                next = expr.nextFire(LocalDateTime.now());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void validateFires(CronExpression expr) {
        try {
            expr.nextFire(LocalDateTime.now());
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("cron expression never fires: " + expr.expression(), e);
        }
    }

    private Method resolveInvokable(Object bean, Method method) {
        if (method.getDeclaringClass().isInstance(bean)) {
            return method;
        }
        try {
            return bean.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private List<Method> collectScheduledMethods(Class<?> type) {
        List<Method> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getAnnotationsByType(Scheduled.class).length == 0) continue;
                String signature = m.getName() + Arrays.toString(m.getParameterTypes());
                if (seen.add(signature)) result.add(m);
            }
            current = current.getSuperclass();
        }
        return result;
    }

    public void shutdown() {
        running = false;
        for (Thread t : tasks) {
            t.interrupt();
        }
        for (Thread t : tasks) {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOG.info("summer scheduling: stopped");
    }
}
