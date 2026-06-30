package cn.jiebaba.summer.test.aop.it;

import cn.jiebaba.summer.core.annotation.Component;

@Component
public class Dep {
    public String who() { return "dep"; }
}