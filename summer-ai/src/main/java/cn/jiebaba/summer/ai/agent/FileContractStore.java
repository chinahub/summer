package cn.jiebaba.summer.ai.agent;

import cn.jiebaba.summer.core.util.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 契约文件存储（默认实现）：一契约一文件（{dir}/{requestId}.json），
 * 临时文件 + 原子替换，进程任意时刻被杀不产生半写文件。
 */
public class FileContractStore implements ContractStore {

    private final Path dir;

    public FileContractStore(Path dir) {
        this.dir = dir;
    }

    @Override
    public A2aContract save(A2aContract contract) {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(contract.requestId() + ".json.tmp");
            Files.writeString(tmp, JsonUtil.toJsonStr(contract), StandardCharsets.UTF_8);
            Files.move(tmp, fileOf(contract.requestId()),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("A2A 契约文件写入失败: " + e.getMessage(), e);
        }
        return contract;
    }

    @Override
    public Optional<A2aContract> find(String requestId) {
        Path file = fileOf(requestId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(parse(requestId, Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<A2aContract> list() {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(this::readSafely)
                    .filter(c -> c != null)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private A2aContract readSafely(Path file) {
        try {
            String name = file.getFileName().toString();
            return parse(name.substring(0, name.length() - ".json".length()),
                    Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException | IOException e) {
            return null;    // 单个坏文件不影响整体扫描
        }
    }

    private Path fileOf(String requestId) {
        return dir.resolve(requestId + ".json");
    }

    private A2aContract parse(String requestId, String json) {
        JsonUtil.JSONObject o = JsonUtil.parseObj(json);
        return new A2aContract(
                requestId,
                o.getStr("messageId"),
                o.getStr("conversationId"),
                o.getStr("traceId"),
                o.getStr("direction"),
                o.getStr("peerUrl"),
                o.getStr("callbackUrl"),
                o.getStr("skill"),
                o.getStr("input"),
                o.getStr("feedback"),
                o.getStr("context"),
                o.getStr("status"),
                o.getStr("output"),
                o.getStr("evidence"),
                o.getStr("error"),
                o.getStr("createdAt"),
                o.getStr("updatedAt"));
    }
}
