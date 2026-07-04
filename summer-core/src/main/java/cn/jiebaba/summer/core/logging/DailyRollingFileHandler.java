package cn.jiebaba.summer.core.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.ErrorManager;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * 自包含的文件 {@link Handler}，每天滚动一次，可选的单文件大小上限与历史保留。
 * 纯 JDK 实现，无第三方依赖。同时支持基于时间的滚动与基于时间+大小的滚动。
 */
public final class DailyRollingFileHandler extends Handler {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path directory;
    private final String filePrefix;
    private final long maxSizeBytes;
    private final int maxHistory;
    private final boolean sizeRolling;

    private LocalDate currentDate;
    private OutputStream out;
    private Path currentFile;
    private long currentSize;
    private int sizeSegment;

    public DailyRollingFileHandler(String directory, String fileName, long maxSizeBytes, int maxHistory,
                                   boolean sizeRolling) throws IOException {
        this.directory = Paths.get(directory);
        this.filePrefix = fileName;
        this.maxSizeBytes = maxSizeBytes;
        this.maxHistory = maxHistory;
        this.sizeRolling = sizeRolling;
        Files.createDirectories(this.directory);
        this.currentDate = LocalDate.now(ZoneId.systemDefault());
        openCurrent();
    }

    @Override
    /**
     * 写入一条日志记录，按需触发按日滚动与旧文件清理。
     */
    public synchronized void publish(LogRecord record) {
        if (!isLoggable(record) || out == null) return;
        String msg;
        try {
            msg = getFormatter().format(record);
        } catch (Exception e) {
            reportError(null, e, ErrorManager.FORMAT_FAILURE);
            return;
        }
        byte[] bytes = msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (!today.equals(currentDate)) {
            rollover(today);
        } else if (sizeRolling && maxSizeBytes > 0 && currentSize + bytes.length > maxSizeBytes) {
            rollSizeSegment();
        }
        if (out == null) return;
        try {
            out.write(bytes);
            currentSize += bytes.length;
            out.flush();
        } catch (IOException e) {
            reportError(null, e, ErrorManager.WRITE_FAILURE);
        }
    }

    private void rollover(LocalDate today) {
        closeCurrent();
        purgeOlderThan(today);
        currentDate = today;
        sizeSegment = 0;
        try {
            openCurrent();
        } catch (IOException e) {
            reportError(null, e, ErrorManager.OPEN_FAILURE);
        }
    }

    private void rollSizeSegment() {
        closeCurrent();
        sizeSegment++;
        try {
            openCurrent();
        } catch (IOException e) {
            reportError(null, e, ErrorManager.OPEN_FAILURE);
        }
    }

    private Path resolveFile() {
        String date = currentDate.format(DATE_FORMAT);
        String name = sizeSegment == 0
                ? filePrefix + "." + date + ".log"
                : filePrefix + "." + date + "." + sizeSegment + ".log";
        return directory.resolve(name);
    }

    private void openCurrent() throws IOException {
        currentFile = resolveFile();
        currentSize = Files.exists(currentFile) ? Files.size(currentFile) : 0L;
        out = Files.newOutputStream(currentFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    private void closeCurrent() {
        if (out != null) {
            try {
                out.flush();
                out.close();
            } catch (IOException e) {
                reportError(null, e, ErrorManager.CLOSE_FAILURE);
            }
            out = null;
        }
    }

    /**
     * 清理早于保留天数的过期日志文件。
     */
    private void purgeOlderThan(LocalDate today) {
        if (maxHistory <= 0) return;
        LocalDate cutoff = today.minusDays(maxHistory);
        List<Path> matches = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(directory, filePrefix + ".*.log")) {
            for (Path p : ds) matches.add(p);
        } catch (IOException e) {
            reportError(null, e, ErrorManager.GENERIC_FAILURE);
            return;
        }
        matches.sort(Comparator.naturalOrder());
        for (Path p : matches) {
            String name = p.getFileName().toString();
            String[] parts = name.split("\\.");
            for (String part : parts) {
                try {
                    LocalDate fileDate = LocalDate.parse(part, DATE_FORMAT);
                    if (fileDate.isBefore(cutoff)) {
                        Files.deleteIfExists(p);
                    }
                    break;
                } catch (Exception ignore) {
                    // 非日期段，继续扫描各部分
                }
            }
        }
    }

    @Override
    public synchronized void flush() {
        if (out != null) {
            try {
                out.flush();
            } catch (IOException e) {
                reportError(null, e, ErrorManager.FLUSH_FAILURE);
            }
        }
    }

    @Override
    public synchronized void close() {
        closeCurrent();
    }
}
