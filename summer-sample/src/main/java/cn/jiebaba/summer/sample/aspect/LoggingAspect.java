package cn.jiebaba.summer.sample.aspect;

import cn.jiebaba.summer.core.aop.AfterReturning;
import cn.jiebaba.summer.core.aop.Aspect;
import cn.jiebaba.summer.core.aop.Around;
import cn.jiebaba.summer.core.aop.ProceedingJoinPoint;

import java.util.logging.Logger;

@Aspect
public class LoggingAspect {
    private static final Logger LOG = Logger.getLogger(LoggingAspect.class.getName());

    @Around("execution(* io.summer.sample.repository..*.*(..))")
    public Object logAround(ProceedingJoinPoint jp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = jp.proceed();
        LOG.info("[" + jp.getSignature() + "] took " + (System.currentTimeMillis() - start) + "ms");
        return result;
    }

    @AfterReturning("execution(* io.summer.sample.repository..*.save(..))")
    public void afterSave(ProceedingJoinPoint jp) {
        LOG.info("save completed on " + jp.getSignature());
    }
}
