package cn.jiebaba.summer.data.support;

/**
 * 乐观锁冲突异常：{@code @Version} 版本字段的 updateById 受影响行数为 0 时抛出
 * （版本已被他人修改或记录不存在）。调用方可捕获后重试或转为业务冲突响应。
 */
public class OptimisticLockException extends DataAccessException {

    public OptimisticLockException(String message) {
        super(message);
    }
}
