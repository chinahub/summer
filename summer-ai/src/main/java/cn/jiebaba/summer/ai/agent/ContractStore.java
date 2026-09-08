package cn.jiebaba.summer.ai.agent;

import java.util.List;
import java.util.Optional;

/**
 * A2A 契约存储 SPI：委派关系持久化的最小接口。
 * 默认实现 {@link FileContractStore}（每契约一文件，原子替换）；可自行实现为 JDBC 等持久化。
 */
public interface ContractStore {

    /** 保存（新增或整体覆盖）契约，返回原对象。 */
    A2aContract save(A2aContract contract);

    /** 按 requestId 查找。 */
    Optional<A2aContract> find(String requestId);

    /** 全部契约（诊断与恢复扫描）。 */
    List<A2aContract> list();

    /** 在途的出域契约（我方委派出去、等待结果）。 */
    default List<A2aContract> listOutboundActive() {
        return list().stream()
                .filter(c -> A2aContract.DIR_OUTBOUND.equals(c.direction()) && c.isActive())
                .toList();
    }

    /** 在途的入域契约（对方委派进来、未完成）。 */
    default List<A2aContract> listInboundActive() {
        return list().stream()
                .filter(c -> A2aContract.DIR_INBOUND.equals(c.direction()) && c.isActive())
                .toList();
    }
}
