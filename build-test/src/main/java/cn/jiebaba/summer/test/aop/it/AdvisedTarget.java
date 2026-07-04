package cn.jiebaba.summer.test.aop.it;

import cn.jiebaba.summer.core.annotation.Service;

/** 无接口的 Bean，由 ProxyAdvisor 代理（与 @Transactional 的工作方式一致）。 */
@Service
public class AdvisedTarget {
    public String ping() { return "pong"; }
}
