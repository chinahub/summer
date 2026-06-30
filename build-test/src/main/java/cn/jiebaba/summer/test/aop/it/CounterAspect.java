package cn.jiebaba.summer.test.aop.it;

import cn.jiebaba.summer.core.aop.Around;
import cn.jiebaba.summer.core.aop.Aspect;
import cn.jiebaba.summer.core.aop.ProceedingJoinPoint;

@Aspect
public class CounterAspect {
    public int count = 0;

    @Around("execution(* cn.jiebaba.summer.test.aop.it.NoInterfaceService.work(..))")
    public Object aroundWork(ProceedingJoinPoint jp) throws Throwable {
        count++;
        return jp.proceed();
    }

    @Around("execution(* cn.jiebaba.summer.test.aop.it.GreetServiceImpl.greet(..))")
    public Object aroundGreet(ProceedingJoinPoint jp) throws Throwable {
        count++;
        return jp.proceed();
    }
}