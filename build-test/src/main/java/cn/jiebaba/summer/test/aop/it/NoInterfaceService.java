package cn.jiebaba.summer.test.aop.it;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.core.annotation.PostConstruct;
import cn.jiebaba.summer.core.annotation.Service;

@Service
public class NoInterfaceService {
    private Dep dep;
    private boolean inited = false;

    @Autowired
    public void setDep(Dep dep) { this.dep = dep; }

    @PostConstruct
    public void init() { this.inited = true; }

    public boolean depInjected() { return dep != null; }
    public boolean inited() { return inited; }
    public String work() { return "worked"; }
}