package cn.jiebaba.summer.data.support;

import cn.jiebaba.summer.data.annotation.IdType;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class IdGenerator {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);
    private IdGenerator() {}

    public static Object generate(IdType type) {
        return switch (type) {
            case ASSIGN_ID -> nextSnowflakeLike();
            case ASSIGN_UUID -> UUID.randomUUID().toString().replace("-", "");
            case AUTO, INPUT -> null;
        };
    }

    /** A simple monotonic id: millis << 20 | counter (no MAC dependency, single-JVM). */
    private static long nextSnowflakeLike() {
        long time = System.currentTimeMillis();
        int seq = SEQUENCE.incrementAndGet() & 0xFFFFF;
        return (time << 20) | seq;
    }
}
