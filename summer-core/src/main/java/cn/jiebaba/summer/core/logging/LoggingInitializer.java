package cn.jiebaba.summer.core.logging;

import cn.jiebaba.summer.core.env.Environment;

import java.io.IOException;
import java.util.logging.ErrorManager;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Initializes {@code java.util.logging} with a console channel and a rolling
 * file channel according to {@link LogProperties}. Should be called early, before
 * the IoC container refreshes, so that startup logs are captured to file.
 */
public final class LoggingInitializer {

    private static final String SUMMER_FILE_HANDLER = "summer-file";
    private static final String SUMMER_CONSOLE_HANDLER = "summer-console";

    private LoggingInitializer() {}

    public static LogProperties initialize(Environment environment) {
        LogProperties props = new LogProperties(environment);
        Logger root = Logger.getLogger("");
        removeSummerHandlers(root);

        root.setLevel(props.rootLevel());
        applyPackageLevels(props);

        Formatter formatter = props.singleLine()
                ? new SingleLineFormatter()
                : new SimpleFormatter();

        if (props.consoleEnabled()) {
            Handler console = new StdConsoleHandler();
            console.setFormatter(formatter);
            console.setLevel(Level.ALL);
            root.addHandler(tag(console, SUMMER_CONSOLE_HANDLER));
        }

        if (props.fileEnabled()) {
            Handler fileHandler = createFileHandler(props, formatter);
            if (fileHandler != null) {
                fileHandler.setFormatter(formatter);
                fileHandler.setLevel(Level.ALL);
                root.addHandler(tag(fileHandler, SUMMER_FILE_HANDLER));
                root.info("summer logging: file channel at " + props.filePath()
                        + " (policy=" + props.rollingPolicy() + ", maxSize=" + props.maxSizeBytes()
                        + ", maxHistory=" + props.maxHistory() + ")");
            }
        }
        root.info("summer logging initialized (root level=" + props.rootLevel()
                + ", console=" + props.consoleEnabled() + ", file=" + props.fileEnabled() + ")");
        return props;
    }

    private static Handler createFileHandler(LogProperties props, Formatter formatter) {
        try {
            return switch (props.rollingPolicy()) {
                case SIZE -> {
                    String pattern = java.nio.file.Paths.get(props.filePath(), props.fileName() + "-%g.log").toString();
                    yield new FileHandler(pattern, props.maxSizeBytes(), props.maxHistory(), true);
                }
                case TIME -> new DailyRollingFileHandler(
                        props.filePath(), props.fileName(), 0, props.maxHistory(), false);
                case SIZE_TIME -> new DailyRollingFileHandler(
                        props.filePath(), props.fileName(), props.maxSizeBytes(), props.maxHistory(), true);
            };
        } catch (IOException e) {
            Logger.getLogger("").warning("summer logging: failed to create file handler: " + e.getMessage());
            return null;
        }
    }

    private static void applyPackageLevels(LogProperties props) {
        props.packageLevels().forEach((pkg, level) -> Logger.getLogger(pkg).setLevel(level));
    }

    private static void removeSummerHandlers(Logger root) {
        for (Handler h : root.getHandlers()) {
            root.removeHandler(h);
            h.close();
        }
    }

    private static Handler tag(Handler handler, String tag) {
        handler.setErrorManager(new TaggedErrorManager(tag));
        return handler;
    }

    private static final class StdConsoleHandler extends Handler {
        @Override
        public void publish(java.util.logging.LogRecord record) {
            if (!isLoggable(record)) return;
            String message = getFormatter().format(record);
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                System.err.print(message);
                System.err.flush();
            } else {
                System.out.print(message);
                System.out.flush();
            }
        }

        @Override public void flush() {
            System.out.flush();
            System.err.flush();
        }

        @Override public void close() {
            flush();
        }
    }

    private static final class TaggedErrorManager extends ErrorManager {
        final String tag;
        TaggedErrorManager(String tag) { this.tag = tag; }
    }
}
