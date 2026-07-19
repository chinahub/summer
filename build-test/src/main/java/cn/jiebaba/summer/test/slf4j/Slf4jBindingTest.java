package cn.jiebaba.summer.test.slf4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * 验证 Summer 内置的 SLF4J binding 会把每一条 SLF4J 调用（即 Lombok @Slf4j 生成的
 * 调用形式）路由到 java.util.logging，且无需桥接 jar。
 */
public class Slf4jBindingTest {

    private static final String LOGGER_NAME = "cn.jiebaba.summer.test.slf4j.Slf4jBindingTest";

    private java.util.logging.Logger jul;
    private CapturingHandler handler;

    @BeforeEach
    void setUp() {
        jul = java.util.logging.Logger.getLogger(LOGGER_NAME);
        jul.setUseParentHandlers(false);
        jul.setLevel(Level.ALL);
        handler = new CapturingHandler();
        handler.setLevel(Level.ALL);
        jul.addHandler(handler);
    }

    @AfterEach
    void tearDown() {
        if (handler != null) jul.removeHandler(handler);
        jul.setUseParentHandlers(true);
    }

    @Test
    void slf4jRoutesToJulWithPlaceholder() {
        Logger log = LoggerFactory.getLogger(LOGGER_NAME);
        log.info("hello {}", "summer");
        Assertions.assertEquals(1, handler.records.size());
        LogRecord r = handler.records.get(0);
        Assertions.assertEquals(Level.INFO, r.getLevel());
        Assertions.assertEquals("hello summer", r.getMessage());
        Assertions.assertEquals(LOGGER_NAME, r.getLoggerName());
    }

    @Test
    void trailingThrowableIsAttached() {
        Logger log = LoggerFactory.getLogger(LOGGER_NAME);
        RuntimeException ex = new RuntimeException("boom");
        log.error("failed at step {}", 3, ex);
        Assertions.assertEquals(1, handler.records.size());
        LogRecord r = handler.records.get(0);
        Assertions.assertEquals(Level.SEVERE, r.getLevel());
        Assertions.assertEquals("failed at step 3", r.getMessage());
        Assertions.assertTrue(r.getThrown() == ex, "expected attached throwable");
    }

    @Test
    void allLevelsMapToJul() {
        Logger log = LoggerFactory.getLogger(LOGGER_NAME);
        log.trace("t");
        log.debug("d");
        log.info("i");
        log.warn("w");
        log.error("e");
        Assertions.assertEquals(5, handler.records.size());
        Assertions.assertEquals(Level.FINER, handler.records.get(0).getLevel());
        Assertions.assertEquals(Level.FINE, handler.records.get(1).getLevel());
        Assertions.assertEquals(Level.INFO, handler.records.get(2).getLevel());
        Assertions.assertEquals(Level.WARNING, handler.records.get(3).getLevel());
        Assertions.assertEquals(Level.SEVERE, handler.records.get(4).getLevel());
    }

    @Test
    void isEnabledReflectsJulLevel() {
        Logger log = LoggerFactory.getLogger(LOGGER_NAME);
        jul.setLevel(Level.WARNING);
        Assertions.assertFalse(log.isTraceEnabled());
        Assertions.assertFalse(log.isDebugEnabled());
        Assertions.assertFalse(log.isInfoEnabled());
        Assertions.assertTrue(log.isWarnEnabled());
        Assertions.assertTrue(log.isErrorEnabled());
    }

    static final class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() {}
        @Override public void close() {}
    }
}
