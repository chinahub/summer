package cn.jiebaba.summer.test.aop.it;

import cn.jiebaba.summer.core.annotation.Service;

@Service
public class PingedImpl implements Pinged {
    @Override
    public String ping() { return "pong2"; }
}