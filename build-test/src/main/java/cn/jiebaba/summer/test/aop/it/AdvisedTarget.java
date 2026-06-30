package cn.jiebaba.summer.test.aop.it;

import cn.jiebaba.summer.core.annotation.Service;

/** No-interface bean advised by a ProxyAdvisor (mirrors how @Transactional works). */
@Service
public class AdvisedTarget {
    public String ping() { return "pong"; }
}