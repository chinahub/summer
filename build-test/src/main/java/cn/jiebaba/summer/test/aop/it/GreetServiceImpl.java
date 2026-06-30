package cn.jiebaba.summer.test.aop.it;

import cn.jiebaba.summer.core.annotation.Service;

@Service
public class GreetServiceImpl implements GreetService {
    @Override
    public String greet() { return "hi"; }
}