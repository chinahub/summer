package cn.jiebaba.summer.core.logging.slf4j;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * SLF4J 2.x 服务提供者，将每一条 SLF4J 调用路由到 Summer 的 java.util.logging 管道。
 * 当 summer-core 与 slf4j-api 一同位于 classpath 时，由 SLF4J 的 ServiceLoader 自动选中，
 * 因此无需任何桥接 jar（slf4j-jdk14 / jul-to-slf4j），也无需手动装配。
 */
public final class SummerSlf4jServiceProvider implements SLF4JServiceProvider {

    private final SummerJulLoggerFactory loggerFactory = new SummerJulLoggerFactory();
    private final BasicMarkerFactory markerFactory = new BasicMarkerFactory();
    private final SummerMdcAdapter mdcAdapter = new SummerMdcAdapter();

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion() {
        return "2.0.17";
    }

    @Override
    public void initialize() {
        // 无需操作：JUL 由 LoggingInitializer 配置。
    }
}
