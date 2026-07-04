package cn.jiebaba.summer.test.slf4j;

import cn.jiebaba.summer.core.test.AfterEach;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.BeforeEach;
import cn.jiebaba.summer.core.test.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Verifies Summer's built-in SLF4J binding routes every SLF4J call (the exact
 * call Lombok @Slf4j generates) into java.util.logging. Requires no bridge jar.
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
        Assert.assertEquals(1, handler.records.size());
        LogRecord r = handler.records.get(0);
        Assert.assertEquals(Level.INFO, r.getLevel());
        Assert.assertEquals("hello summer", r.getMessage());
        Assert.assertEquals(LOGGER_NAME, r.getLoggerName());
    }

    @Test
    void trailingThrowableIsAttached() {
        Logger log = LoggerFactory.getLogger(LOGGER_NAME);
        RuntimeException ex = new RuntimeException("boom");
        log.error("failed at step {}", 3, ex);
        Assert.assertEquals(1, handler.records.size());
        LogRecord r = handler.records.get(0);
        Assert.assertEquals(Level.SEVERE, r.getLevel());
        Assert.assertEquals("failed at step 3", r.getMessage());
        Assert.assertTrue(r.getThrown() == ex, "expected attached throwable");
    }

    @Test
    void allLevelsMapToJul() {
        Logger log = LoggerFactory.getLogger(LOGGER_NAME);
        log.trace("t");
        log.debug("d");
        log.info("i");
        log.warn("w");
        log.error("e");
        Assert.assertEquals(5, handler.records.size());
        Assert.assertEquals(Level.FINER, handler.records.get(0).getLevel());
        Assert.assertEquals(Level.FINE, handler.records.get(1).getLevel());
        Assert.assertEquals(Level.INFO, handler.records.get(2).getLevel());
        Assert.assertEquals(Level.WARNING, handler.records.get(3).getLevel());
        Assert.assertEquals(Level.SEVERE, handler.records.get(4).getLevel());
    }

    @Test
    void isEnabledReflectsJulLevel() {
        Logger log = LoggerFactory.getLogger(LOGGER_NAME);
        jul.setLevel(Level.WARNING);
        Assert.assertFalse(log.isTraceEnabled());
        Assert.assertFalse(log.isDebugEnabled());
        Assert.assertFalse(log.isInfoEnabled());
        Assert.assertTrue(log.isWarnEnabled());
        Assert.assertTrue(log.isErrorEnabled());
    }

    static final class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();
        @Override public void publish(LogRecord record) { records.add(record); }
        @Override public void flush() {}
        @Override public void close() {}
    }
}