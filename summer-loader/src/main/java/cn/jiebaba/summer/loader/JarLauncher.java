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
 * Executable-jar launcher for summer applications.
 *
 * <p>Jar layout (Spring Boot style, dependencies kept as nested jars):
 * <pre>
 *   cn/jiebaba/summer/loader/JarLauncher.class   launcher, at jar root
 *   BOOT-INF/classes/...                          application classes &amp; resources
 *   BOOT-INF/lib/*.jar                            dependency jars (unexploded)
 *   META-INF/MANIFEST.MF                          Main-Class=JarLauncher, Start-Class=app main
 * </pre>
 *
 * <p>summer's {@code ClassPathScanner} enumerates classes by reading the
 * {@code java.class.path} / {@code jdk.module.path} system properties directly
 * rather than traversing ClassLoader URLs, so this launcher materialises the
 * nested {@code BOOT-INF} entries onto disk, rebuilds {@code java.class.path}
 * to point at them, and launches the application through a {@link URLClassLoader}
 * over the same roots. {@code application.yml} is then resolvable via the
 * context ClassLoader and the existing scanner works unchanged.
 */
public final class JarLauncher {

    private static final String CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String LIB_PREFIX = "BOOT-INF/lib/";
    private static final String START_CLASS = "Start-Class";
    private static final String WORK_PREFIX = "summer-boot-";
    private static final String LIB_CACHE_DIR = "summer-boot-lib-cache";
    private static final long STALE_THRESHOLD_MS = 2 * 60 * 60 * 1000L;

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