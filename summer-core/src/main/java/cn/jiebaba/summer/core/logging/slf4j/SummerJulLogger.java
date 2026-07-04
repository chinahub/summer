package cn.jiebaba.summer.core.logging.slf4j;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.util.logging.Level;

/**
 * SLF4J {@link Logger} backed by a {@link java.util.logging.Logger}. Each call is
 * translated to the corresponding JUL level so that all output flows through
 * Summer's single logging pipeline configured by {@code LoggingInitializer}.
 * {@code {}} placeholders are resolved with SLF4J's {@link MessageFormatter}; a
 * trailing {@link Throwable} that is not consumed by a placeholder is attached to
 * the JUL {@code LogRecord} so the single-line formatter prints its stack trace.
 * Marker arguments are ignored, matching a minimal no-frills binding.
 */
final class SummerJulLogger implements Logger {

    private static final Level TRACE = Level.FINER;
    private static final Level DEBUG = Level.FINE;
    private static final Level INFO = Level.INFO;
    private static final Level WARN = Level.WARNING;
    private static final Level ERROR = Level.SEVERE;

    private final java.util.logging.Logger jul;

    SummerJulLogger(java.util.logging.Logger jul) {
        this.jul = jul;
    }

    @Override
    public String getName() {
        return jul.getName();
    }

    // ---- helpers ----------------------------------------------------------
    private void emit(Level level, String msg) {
        jul.log(level, msg);
    }

    private void emit(Level level, String msg, Throwable thrown) {
        jul.log(level, msg, thrown);
    }

    private void emitFormatted(Level level, String format, Object... args) {
        if (!jul.isLoggable(level)) return;
        FormattingTuple tuple = MessageFormatter.arrayFormat(format, args);
        if (tuple.getThrowable() != null) {
            jul.log(level, tuple.getMessage(), tuple.getThrowable());
        } else {
            jul.log(level, tuple.getMessage());
        }
    }

    // ---- trace ------------------------------------------------------------
    @Override public boolean isTraceEnabled() { return jul.isLoggable(TRACE); }
    @Override public void trace(String msg) { emit(TRACE, msg); }
    @Override public void trace(String format, Object arg) { emitFormatted(TRACE, format, arg); }
    @Override public void trace(String format, Object arg1, Object arg2) { emitFormatted(TRACE, format, arg1, arg2); }
    @Override public void trace(String format, Object... arguments) { emitFormatted(TRACE, format, arguments); }
    @Override public void trace(String msg, Throwable t) { emit(TRACE, msg, t); }
    @Override public boolean isTraceEnabled(Marker marker) { return jul.isLoggable(TRACE); }
    @Override public void trace(Marker marker, String msg) { emit(TRACE, msg); }
    @Override public void trace(Marker marker, String format, Object arg) { emitFormatted(TRACE, format, arg); }
    @Override public void trace(Marker marker, String format, Object arg1, Object arg2) { emitFormatted(TRACE, format, arg1, arg2); }
    @Override public void trace(Marker marker, String format, Object... args) { emitFormatted(TRACE, format, args); }
    @Override public void trace(Marker marker, String msg, Throwable t) { emit(TRACE, msg, t); }

    // ---- debug ------------------------------------------------------------
    @Override public boolean isDebugEnabled() { return jul.isLoggable(DEBUG); }
    @Override public void debug(String msg) { emit(DEBUG, msg); }
    @Override public void debug(String format, Object arg) { emitFormatted(DEBUG, format, arg); }
    @Override public void debug(String format, Object arg1, Object arg2) { emitFormatted(DEBUG, format, arg1, arg2); }
    @Override public void debug(String format, Object... arguments) { emitFormatted(DEBUG, format, arguments); }
    @Override public void debug(String msg, Throwable t) { emit(DEBUG, msg, t); }
    @Override public boolean isDebugEnabled(Marker marker) { return jul.isLoggable(DEBUG); }
    @Override public void debug(Marker marker, String msg) { emit(DEBUG, msg); }
    @Override public void debug(Marker marker, String format, Object arg) { emitFormatted(DEBUG, format, arg); }
    @Override public void debug(Marker marker, String format, Object arg1, Object arg2) { emitFormatted(DEBUG, format, arg1, arg2); }
    @Override public void debug(Marker marker, String format, Object... args) { emitFormatted(DEBUG, format, args); }
    @Override public void debug(Marker marker, String msg, Throwable t) { emit(DEBUG, msg, t); }

    // ---- info -------------------------------------------------------------
    @Override public boolean isInfoEnabled() { return jul.isLoggable(INFO); }
    @Override public void info(String msg) { emit(INFO, msg); }
    @Override public void info(String format, Object arg) { emitFormatted(INFO, format, arg); }
    @Override public void info(String format, Object arg1, Object arg2) { emitFormatted(INFO, format, arg1, arg2); }
    @Override public void info(String format, Object... arguments) { emitFormatted(INFO, format, arguments); }
    @Override public void info(String msg, Throwable t) { emit(INFO, msg, t); }
    @Override public boolean isInfoEnabled(Marker marker) { return jul.isLoggable(INFO); }
    @Override public void info(Marker marker, String msg) { emit(INFO, msg); }
    @Override public void info(Marker marker, String format, Object arg) { emitFormatted(INFO, format, arg); }
    @Override public void info(Marker marker, String format, Object arg1, Object arg2) { emitFormatted(INFO, format, arg1, arg2); }
    @Override public void info(Marker marker, String format, Object... args) { emitFormatted(INFO, format, args); }
    @Override public void info(Marker marker, String msg, Throwable t) { emit(INFO, msg, t); }

    // ---- warn -------------------------------------------------------------
    @Override public boolean isWarnEnabled() { return jul.isLoggable(WARN); }
    @Override public void warn(String msg) { emit(WARN, msg); }
    @Override public void warn(String format, Object arg) { emitFormatted(WARN, format, arg); }
    @Override public void warn(String format, Object arg1, Object arg2) { emitFormatted(WARN, format, arg1, arg2); }
    @Override public void warn(String format, Object... arguments) { emitFormatted(WARN, format, arguments); }
    @Override public void warn(String msg, Throwable t) { emit(WARN, msg, t); }
    @Override public boolean isWarnEnabled(Marker marker) { return jul.isLoggable(WARN); }
    @Override public void warn(Marker marker, String msg) { emit(WARN, msg); }
    @Override public void warn(Marker marker, String format, Object arg) { emitFormatted(WARN, format, arg); }
    @Override public void warn(Marker marker, String format, Object arg1, Object arg2) { emitFormatted(WARN, format, arg1, arg2); }
    @Override public void warn(Marker marker, String format, Object... args) { emitFormatted(WARN, format, args); }
    @Override public void warn(Marker marker, String msg, Throwable t) { emit(WARN, msg, t); }

    // ---- error ------------------------------------------------------------
    @Override public boolean isErrorEnabled() { return jul.isLoggable(ERROR); }
    @Override public void error(String msg) { emit(ERROR, msg); }
    @Override public void error(String format, Object arg) { emitFormatted(ERROR, format, arg); }
    @Override public void error(String format, Object arg1, Object arg2) { emitFormatted(ERROR, format, arg1, arg2); }
    @Override public void error(String format, Object... arguments) { emitFormatted(ERROR, format, arguments); }
    @Override public void error(String msg, Throwable t) { emit(ERROR, msg, t); }
    @Override public boolean isErrorEnabled(Marker marker) { return jul.isLoggable(ERROR); }
    @Override public void error(Marker marker, String msg) { emit(ERROR, msg); }
    @Override public void error(Marker marker, String format, Object arg) { emitFormatted(ERROR, format, arg); }
    @Override public void error(Marker marker, String format, Object arg1, Object arg2) { emitFormatted(ERROR, format, arg1, arg2); }
    @Override public void error(Marker marker, String format, Object... args) { emitFormatted(ERROR, format, args); }
    @Override public void error(Marker marker, String msg, Throwable t) { emit(ERROR, msg, t); }
}