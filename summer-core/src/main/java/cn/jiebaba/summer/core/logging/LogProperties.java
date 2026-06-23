package cn.jiebaba.summer.core.logging;

import cn.jiebaba.summer.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Logging configuration read from the {@link Environment}. Supports a console
 * channel and a file channel with size-based and/or time-based (daily) rolling.
 */
public final class LogProperties {

    public enum RollingPolicy { SIZE, TIME, SIZE_TIME }

    private final Level rootLevel;
    private final Map<String, Level> packageLevels = new LinkedHashMap<>();
    private final boolean consoleEnabled;
    private final boolean fileEnabled;
    private final String filePath;
    private final String fileName;
    private final RollingPolicy rollingPolicy;
    private final long maxSizeBytes;
    private final int maxHistory;
    private final boolean singleLine;

    public LogProperties(Environment env) {
        this.rootLevel = parseLevel(env.getProperty("logging.level.root", "INFO"), Level.INFO);
        for (String key : env.all().keySet()) {
            if (key.startsWith("logging.level.") && !"logging.level.root".equals(key)) {
                String pkg = key.substring("logging.level.".length());
                Level lvl = parseLevel(env.getProperty(key), this.rootLevel);
                if (lvl != null) this.packageLevels.put(pkg, lvl);
            }
        }
        this.consoleEnabled = env.getProperty("logging.console.enabled", Boolean.class, true);
        this.fileEnabled = env.getProperty("logging.file.enabled", Boolean.class, true);
        this.filePath = env.getProperty("logging.file.path", String.class, "logs");
        this.fileName = env.getProperty("logging.file.name", String.class, "summer");
        this.rollingPolicy = RollingPolicy.valueOf(
                env.getProperty("logging.file.rolling-policy", "time").toUpperCase().replace('-', '_'));
        this.maxSizeBytes = parseSize(env.getProperty("logging.file.max-size", "10MB"));
        this.maxHistory = env.getProperty("logging.file.max-history", Integer.class, 7);
        this.singleLine = env.getProperty("logging.format.single-line", Boolean.class, true);
    }

    public Level rootLevel() { return rootLevel; }
    public Map<String, Level> packageLevels() { return packageLevels; }
    public boolean consoleEnabled() { return consoleEnabled; }
    public boolean fileEnabled() { return fileEnabled; }
    public String filePath() { return filePath; }
    public String fileName() { return fileName; }
    public RollingPolicy rollingPolicy() { return rollingPolicy; }
    public long maxSizeBytes() { return maxSizeBytes; }
    public int maxHistory() { return maxHistory; }
    public boolean singleLine() { return singleLine; }

    static Level parseLevel(String value, Level fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Level.parse(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return switch (value.trim().toUpperCase()) {
                case "TRACE" -> Level.FINER;
                case "DEBUG" -> Level.FINE;
                case "WARN" -> Level.WARNING;
                case "ERROR", "FATAL" -> Level.SEVERE;
                default -> fallback;
            };
        }
    }

    static long parseSize(String text) {
        if (text == null || text.isBlank()) return 10L * 1024 * 1024;
        String s = text.trim().toUpperCase();
        long multiplier = 1;
        if (s.endsWith("KB")) { multiplier = 1024L; s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("MB")) { multiplier = 1024L * 1024; s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("GB")) { multiplier = 1024L * 1024 * 1024; s = s.substring(0, s.length() - 2); }
        else if (s.endsWith("B")) { s = s.substring(0, s.length() - 1); }
        try {
            return Long.parseLong(s.trim()) * multiplier;
        } catch (NumberFormatException e) {
            return 10L * 1024 * 1024;
        }
    }
}
