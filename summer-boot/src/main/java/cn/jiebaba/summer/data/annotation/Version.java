package cn.jiebaba.summer.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 乐观锁版本字段（仅支持 int/Integer/long/Long 类型）：
 * <ul>
 *   <li>insert：版本号为空时自动初始化为 1；</li>
 *   <li>updateById：生成 {@code SET ..., version = version + 1 WHERE id = ? AND version = ?}，
 *       受影响行数为 0 时抛出
 *       {@link cn.jiebaba.summer.data.support.OptimisticLockException}（版本冲突或记录不存在），
 *       成功后实体版本号自增。</li>
 * </ul>
 * 适合并发状态流转（如多实例并行推进子任务）的 CAS 更新。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Version {
}
