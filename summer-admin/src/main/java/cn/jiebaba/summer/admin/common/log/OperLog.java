package cn.jiebaba.summer.admin.common.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：仅标注在管理端写操作上（新增/修改/删除/分配/重置密码等），
 * 由 {@link OperationLogAspect} 经 AOP 代理路径拦截记录。
 * 未被注解的方法绝不产生操作日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 操作模块（落入日志 title），如 {@code "用户管理"}。 */
    String module();

    /**
     * 操作动作：新增/修改/删除/导出/导入/强退/清空 映射为 businessType 1..7，
     * 其余动作（如 分配/重置密码）记为 0（其他）。
     */
    String action();
}
