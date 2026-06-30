package cn.jiebaba.summer.test.aop.cglib;

import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.transaction.Transactional;

/**
 * 无接口、非 final 的 Service，带 {@code @Transactional} 方法。
 * 必须走手写字节码子类代理（CGLIB 风格），事务才生效——这正是
 * {@code docs/使用文档/orm.md} 中"AOP 代理要求"所要验证的场景。
 */
@Service
public class OrderService {

    @Transactional
    public String commitOk() {
        return "committed";
    }

    @Transactional
    public String throwsAndRollback() {
        throw new RuntimeException("boom");
    }

    @Transactional(rollbackFor = Exception.class)
    public String throwsCheckedAndRollback() throws Exception {
        throw new Exception("checked");
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public String throwsButNoRollback() {
        throw new RuntimeException("ignored");
    }
}