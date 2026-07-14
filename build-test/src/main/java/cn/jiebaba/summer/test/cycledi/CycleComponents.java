package cn.jiebaba.summer.test.cycledi;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.core.annotation.Component;

/** 构造器注入循环依赖组件（顶层包级类）：CycleA 构造需 CycleB，CycleB 构造需 CycleA，无法解析。 */
@Component
class CycleA {
    public CycleA(CycleB b) {
    }
}

@Component
class CycleB {
    @Autowired
    public CycleB(CycleA a) {
    }
}
