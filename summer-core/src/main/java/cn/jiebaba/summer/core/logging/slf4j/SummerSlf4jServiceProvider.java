package cn.jiebaba.summer.core.logging.slf4j;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * SLF4J 2.x service provider that routes every SLF4J call into Summer's
 * java.util.logging pipeline. Selected automatically by SLF4J's ServiceLoader
 * when summer-core is on the classpath alongside slf4j-api, so no bridge jar
 * (slf4j-jdk14 / jul-to-slf4j) and no manual wiring are ever required.
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
        // Nothing to do: JUL is configured by LoggingInitializer.
    }
}