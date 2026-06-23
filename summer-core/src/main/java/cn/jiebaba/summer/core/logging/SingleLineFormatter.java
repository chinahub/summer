package cn.jiebaba.summer.core.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/** Compact single-line log formatter: timestamp LEVEL [thread] logger - message. */
public final class SingleLineFormatter extends Formatter {

    private final DateTimeFormatter timeFormat =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder(160);
        sb.append(timeFormat.format(Instant.ofEpochMilli(record.getMillis())))
          .append(' ').append(pad(record.getLevel().getName(), 7))
          .append(" [").append(threadName(record.getThreadID())).append("] ")
          .append(abbreviate(record.getLoggerName())).append(" - ")
          .append(formatMessage(record));
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            sb.append('\n');
            StringWriter sw = new StringWriter();
            thrown.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(width);
        sb.append(s);
        for (int i = s.length(); i < width; i++) sb.append(' ');
        return sb.toString();
    }

    private static String abbreviate(String loggerName) {
        if (loggerName == null) return "";
        int lastDot = loggerName.lastIndexOf('.');
        return lastDot < 0 ? loggerName : loggerName.substring(lastDot + 1);
    }

    private static String threadName(int threadId) {
        Thread current = Thread.currentThread();
        return current.getName();
    }
}
