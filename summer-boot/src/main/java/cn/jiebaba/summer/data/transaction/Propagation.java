package cn.jiebaba.summer.data.transaction;

/**
 * 事务传播行为（{@link Transactional#propagation()}）。
 * <ul>
 *   <li>{@link #REQUIRED}（默认）：有活动事务则加入，无则开启新事务；</li>
 *   <li>{@link #REQUIRES_NEW}：总是开启独立新事务并挂起外层事务（独立提交/回滚，
 *       结束后外层事务继续），适合"无论外层成败都要落账"的审计/计量类操作。</li>
 * </ul>
 * 暂不支持 NESTED（保存点）等其余传播行为。
 */
public enum Propagation {
    REQUIRED,
    REQUIRES_NEW
}
