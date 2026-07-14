package cn.jiebaba.summer.test.protodi;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.core.annotation.Component;
import cn.jiebaba.summer.core.annotation.Scope;

/** 原型作用域循环依赖组件（顶层包级类）：两个原型互相字段注入，无法解析。 */
@Component
@Scope("prototype")
class ProtoA {
    @Autowired
    ProtoB b;
}

@Component
@Scope("prototype")
class ProtoB {
    @Autowired
    ProtoA a;
}
