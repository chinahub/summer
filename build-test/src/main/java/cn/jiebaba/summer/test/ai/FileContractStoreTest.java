package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.agent.A2aContract;
import cn.jiebaba.summer.ai.agent.FileContractStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A2A 契约文件存储：落盘/读取回环、状态更新覆盖、活性扫描、坏文件容错。 */
public class FileContractStoreTest {

    private static A2aContract contract(String requestId, String direction, String status) {
        return new A2aContract(requestId, "msg-1", "conv-1", "trace-1",
                direction, "http://peer", "http://self", "review", "输入", null, "上下文",
                status, null, null, null, "2026-09-09T00:00:00", "2026-09-09T00:00:00");
    }

    @Test
    public void saveAndFindRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("a2a-store");
        FileContractStore store = new FileContractStore(dir);

        store.save(contract("req-1", A2aContract.DIR_OUTBOUND, A2aContract.ST_PENDING));
        Assertions.assertTrue(Files.isRegularFile(dir.resolve("req-1.json")));

        A2aContract loaded = store.find("req-1").orElseThrow();
        Assertions.assertEquals("req-1", loaded.requestId());
        Assertions.assertEquals(A2aContract.DIR_OUTBOUND, loaded.direction());
        Assertions.assertEquals("review", loaded.skill());
        Assertions.assertEquals("上下文", loaded.context());
        Assertions.assertTrue(store.find("missing").isEmpty());
    }

    @Test
    public void updateOverwritesAndActiveScan() throws Exception {
        Path dir = Files.createTempDirectory("a2a-store");
        FileContractStore store = new FileContractStore(dir);
        store.save(contract("req-1", A2aContract.DIR_OUTBOUND, A2aContract.ST_COMPLETED));
        store.save(contract("req-2", A2aContract.DIR_OUTBOUND, A2aContract.ST_PENDING));
        store.save(contract("req-3", A2aContract.DIR_INBOUND, A2aContract.ST_RUNNING));

        Assertions.assertEquals(1, store.listOutboundActive().size());
        Assertions.assertEquals(1, store.listInboundActive().size());
        Assertions.assertEquals(3, store.list().size());
        Assertions.assertTrue(store.listOutboundActive().get(0).requestId().equals("req-2"));

        store.save(contract("req-2", A2aContract.DIR_OUTBOUND, A2aContract.ST_COMPLETED));
        Assertions.assertEquals(A2aContract.ST_COMPLETED, store.find("req-2").orElseThrow().status());
        Assertions.assertEquals(List.of(), store.listOutboundActive());
    }

    @Test
    public void missingDirYieldsEmptyList() {
        FileContractStore store = new FileContractStore(Path.of(
                System.getProperty("java.io.tmpdir"), "a2a-store-not-exist-" + System.nanoTime()));
        Assertions.assertEquals(List.of(), store.list());
        Assertions.assertTrue(store.find("any").isEmpty());
    }
}
