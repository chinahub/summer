package cn.jiebaba.summer.test.aop.cglib;

import cn.jiebaba.summer.data.transaction.Transactional;

/**
 * final 类，带 {@code @Transactional} 方法。无 {@code @Service}（不参与包扫描），
 * 仅在 final 类无法子类代理的失败用例中通过手动注册 BeanDefinition 触发。
 */
public final class FinalOrderService {

    @Transactional
    public String placeOrder() {
        return "placed";
    }
}