package cn.jiebaba.summer.loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * summer 应用的可执行 jar 启动器。
 *
 * <p>jar 布局（Spring Boot 风格，依赖以嵌套 jar 形式存放）：
 * <pre>
 *   cn/jiebaba/summer/loader/JarLauncher.class   启动器，位于 jar 根目录
 *   BOOT-INF/classes/...                          应用类与资源
 *   BOOT-INF/lib/*.jar                            依赖 jar（不解压）
 *   META-INF/MANIFEST.MF                          Main-Class=JarLauncher，Start-Class=应用主类
 * </pre>
 *
 * <p>summer 的 {@code ClassPathScanner} 通过直接读取
 * {@code java.class.path} / {@code jdk.module.path} 系统属性来枚举类，
 * 而非遍历 ClassLoader 的 URL，因此本启动器将嵌套的 {@code BOOT-INF} 条目落地到磁盘，
 * 重建 {@code java.class.path} 指向它们，并通过基于相同根的 {@link URLClassLoader}
 * 启动应用。这样 {@code application.yml} 便能通过上下文 ClassLoader 解析，
 * 现有扫描器无需改动即可工作。
 */
public final class JarLauncher {

    private static final String CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String LIB_PREFIX = "BOOT-INF/lib/";
    private static final String START_CLASS = "Start-Class";
    private static final String WORK_PREFIX = "summer-boot-";
    private static final String LIB_CACHE_DIR = "summer-boot-lib-cache";
    private static final long STALE_THRESHOLD_MS = 2 * 60 * 60 * 1000L;

    /**
     * 启动入口：清理过期工作目录，从自身 jar 解析 Start-Class，将 {@code BOOT-INF/classes}
     * 与 {@code BOOT-INF/lib} 落地到磁盘并重建 {@code java.class.path}，最后通过
     * {@link URLClassLoader} 反射调用应用主类的 {@code main} 方法。
     *
     * @param args 透传给应用主类的启动参数
     * @throws Exception 当清单缺失、提取失败或启动主类出错时抛出
     */
    public static void main(String[] args) throws Exception {
        cleanStaleWorkDirs();

        Path jar = ownJarPath();
        String startClass;
        List<URL> urls = new ArrayList<>();
        List<Path> classpathRoots = new ArrayList<>();

        try (JarFile boot = new JarFile(jar.toFile())) {
            Manifest manifest = boot.getManifest();
            if (manifest == null) {
                throw new IllegalStateException("No manifest in " + jar);
            }
            startClass = manifest.getMainAttributes().getValue(START_CLASS);
            if (startClass == null || startClass.isBlank()) {
                throw new IllegalStateException("Missing '" + START_CLASS + "' manifest attribute in " + jar);
            }

            Path work = Files.createTempDirectory(WORK_PREFIX);
            Path classesDir = Files.createDirectories(work.resolve("classes"));

            extractClasses(boot, classesDir);
            urls.add(classesDir.toUri().toURL());
            classpathRoots.add(classesDir);

            Path libCache = Path.of(System.getProperty("java.io.tmpdir"), LIB_CACHE_DIR);
            Files.createDirectories(libCache);
            for (Path lib : extractLibs(boot, libCache, work.resolve("lib"))) {
                urls.add(lib.toUri().toURL());
                classpathRoots.add(lib);
            }
        }

        StringBuilder cp = new StringBuilder();
        for (int i = 0; i < classpathRoots.size(); i++) {
            if (i > 0) {
                cp.append(File.pathSeparatorChar);
            }
            cp.append(classpathRoots.get(i));
        }
        System.setProperty("java.class.path", cp.toString());

        URLClassLoader loader = new URLClassLoader(
                urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
        Thread.currentThread().setContextClassLoader(loader);

        Class<?> mainClass = Class.forName(startClass, true, loader);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }

    private static Path ownJarPath() throws Exception {
        URL url = JarLauncher.class.getProtectionDomain().getCodeSource().getLocation();
        if (url == null) {
            throw new IllegalStateException("Cannot determine launcher jar location");
        }
        return Path.of(url.toURI());
    }

    /**
     * 将 {@code BOOT-INF/classes} 下的所有条目提取到目标目录，做路径穿越校验后写入文件。
     *
     * @param boot 可执行 jar
     * @param dest 应用类落地目录
     * @throws IOException 写入文件时发生 I/O 错误
     */
    private static void extractClasses(JarFile boot, Path dest) throws IOException {
        Enumeration<JarEntry> entries = boot.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(CLASSES_PREFIX) || entry.isDirectory()) {
                continue;
            }
            String relative = name.substring(CLASSES_PREFIX.length());
            Path out = dest.resolve(relative).normalize();
            if (!out.startsWith(dest)) {
                continue;
            }
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            try (InputStream in = boot.getInputStream(entry)) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 提取 {@code BOOT-INF/lib} 下的依赖 jar：优先按 CRC 命名缓存到公共缓存目录以复用，
     * 无 CRC 时回退到每次启动的临时 lib 目录。返回各依赖 jar 的落地路径列表。
     *
     * @param boot        可执行 jar
     * @param cacheDir    公共缓存目录（按 CRC 复用）
     * @param fallbackDir 无 CRC 时的回退目录
     * @return 依赖 jar 路径列表
     * @throws IOException 写入文件时发生 I/O 错误
     */
    private static List<Path> extractLibs(JarFile boot, Path cacheDir, Path fallbackDir) throws IOException {
        List<Path> libs = new ArrayList<>();
        Enumeration<JarEntry> entries = boot.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(LIB_PREFIX) || entry.isDirectory() || !name.endsWith(".jar")) {
                continue;
            }
            String base = name.substring(name.lastIndexOf('/') + 1);
            long crc = entry.getCrc();
            Path target;
            if (crc > 0) {
                target = cacheDir.resolve(String.format("%016x-%s", crc, base));
                if (!Files.exists(target)) {
                    try (InputStream in = boot.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } else {
                Files.createDirectories(fallbackDir);
                target = fallbackDir.resolve(base);
                try (InputStream in = boot.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            libs.add(target);
        }
        return libs;
    }

    private static void cleanStaleWorkDirs() {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        long cutoff = System.currentTimeMillis() - STALE_THRESHOLD_MS;
        try (var dirs = Files.newDirectoryStream(tmp, WORK_PREFIX + "*")) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try {
                    if (Files.getLastModifiedTime(dir).toMillis() < cutoff) {
                        deleteTree(dir);
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private JarLauncher() {
    }
}
