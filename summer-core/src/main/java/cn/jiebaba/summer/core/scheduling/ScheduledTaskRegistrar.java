package cn.jiebaba.summer.core.scheduling;

import cn.jiebaba.summer.core.context.ApplicationContext;
import cn.jiebaba.summer.core.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovers {@link Scheduled @Scheduled} methods on context beans and schedules
 * them. Timing uses a single-thread {@link ScheduledThreadPoolExecutor}; each
 * task body runs on its own virtual thread via a
 * {@code newVirtualThreadPerTaskExecutor}, so blocking work does not pin a
 * platform carrier thread.
 */
public final class ScheduledTaskRegistrar {

    private static final Logger LOG = Logger.getLogger(ScheduledTaskRegistrar.class.getName());

    private final ScheduledThreadPoolExecutor scheduler;
    private final ExecutorService worker;
    private final List<java.util.concurrent.ScheduledFuture<?>> futures = new ArrayList<>();

    public ScheduledTaskRegistrar() {
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = Thread.ofPlatform().name("summer-scheduler").unstarted(r);
            t.setDaemon(true);
            return t;
        });
        this.scheduler.setRemoveOnCancelPolicy(true);
        this.worker = Executors.newVirtualThreadPerTaskExecutor();
    }

    public int scheduleAll(ApplicationContext context) {
        int count = 0;
        for (String name : context.getBeanNamesForType(Object.class)) {
            Object bean;
            try { bean = context.getBean(name); } catch (Exception e) { continue; }
            Class<?> type = context.getType(name);
            if (type == null) type = bean.getClass();
            if (java.lang.reflect.Proxy.isProxyClass(bean.getClass()) && type == bean.getClass()) continue;
            for (Method method : type.getDeclaredMethods()) {
                Scheduled[] annotations = method.getAnnotationsByType(Scheduled.class);
                for (Scheduled s : annotations) {
                    schedule(context, bean, method, s);
                    count++;
                }
            }
        }
        if (count > 0) {
            LOG.info("summer scheduling: registered " + count + " scheduled task(s)");
        }
        return count;
    }

    private void schedule(ApplicationContext context, Object bean, Method method, Scheduled s) {
        ReflectionUtils.makeAccessible(method);
        Runnable task = () -> {
            try {
                worker.execute(() -> {
                    try {
                        method.invoke(bean);
                    } catch (Throwable t) {
                        Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : t;
                        LOG.log(Level.WARNING, "Scheduled task " + method + " failed", cause);
                    }
                });
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Failed to dispatch scheduled task " + method, t);
            }
        };

        long initialDelay = Math.max(0, s.initialDelay());
        if (s.cron() != null && !s.cron().isBlank()) {
            scheduleCron(task, s.cron(), initialDelay);
        } else if (s.fixedRate() > 0) {
            futures.add(scheduler.scheduleAtFixedRate(task, initialDelay, s.fixedRate(), TimeUnit.MILLISECONDS));
        } else if (s.fixedDelay() > 0) {
            futures.add(scheduler.scheduleWithFixedDelay(task, initialDelay, s.fixedDelay(), TimeUnit.MILLISECONDS));
        } else {
            LOG.warning("Skipping @Scheduled " + method + ": no cron/fixedRate/fixedDelay configured");
        }
    }

    private void scheduleCron(Runnable task, String cron, long initialDelay) {
        CronExpression expr = new CronExpression(cron);
        Runnable selfRescheduling = new Runnable() {
            @Override public void run() {
                try { task.run(); } finally { reschedule(); }
            }
            void reschedule() {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime next = expr.nextFire(now);
                long delayMs = Math.max(0, java.time.Duration.between(now, next).toMillis());
                futures.add(scheduler.schedule(this, delayMs, TimeUnit.MILLISECONDS));
            }
        };
        LocalDateTime start = LocalDateTime.now().plusNanos(initialDelay * 1_000_000);
        LocalDateTime first = expr.nextFire(start);
        long delayMs = Math.max(0, java.time.Duration.between(LocalDateTime.now(), first).toMillis()) + initialDelay;
        futures.add(scheduler.schedule(selfRescheduling, delayMs, TimeUnit.MILLISECONDS));
    }

    public void shutdown() {
        for (var f : futures) f.cancel(false);
        scheduler.shutdownNow();
        worker.shutdownNow();
        LOG.info("summer scheduling: stopped");
    }
}
