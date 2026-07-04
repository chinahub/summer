package cn.jiebaba.summer.pack;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * 组装 Spring Boot 风格的可执行 jar，采用嵌套布局：
 * <pre>
 *   &lt;root&gt;/cn/jiebaba/summer/loader/JarLauncher.class   启动器（来自 summer-boot-loader，由插件打包）
 *   BOOT-INF/classes/...                                  应用类与资源
 *   BOOT-INF/lib/*.jar                                    依赖 jar（不解压）
 *   META-INF/MANIFEST.MF                                  Main-Class / Start-Class
 * </pre>
 *
 * <p>纯 JDK（java.util.jar）实现，无需第三方依赖，
 * 可在 Maven 插件中运行。条目名使用正斜杠，清单
 * 由 {@link JarOutputStream} 优先写入，以满足 {@code java -jar} 的要求。
 */
final class BootJarBuilder {

    private static final String CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String LIB_PREFIX = "BOOT-INF/lib/";

    private BootJarBuilder() {
    }

    /**
     * 组装可执行 boot jar：创建清单，并依次写入启动器、应用类目录与依赖 jar。
     *
     * @param outputJar     输出 jar 路径
     * @param appClassesDir 应用类与资源所在目录
     * @param libJars       依赖 jar 列表
     * @param loaderJar     启动器 jar（来自 summer-boot-loader）
     * @param mainClass     清单 Main-Class
     * @param startClass    实际启动类
     * @throws IOException 写入 jar 时发生 I/O 错误
     */
    static void build(Path outputJar, Path appClassesDir, List<Path> libJars,
                      Path loaderJar, String mainClass, String startClass) throws IOException {
        if (outputJar.getParent() != null) {
            Files.createDirectories(outputJar.getParent());
        }
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Main-Class", mainClass);
        attrs.putValue("Start-Class", startClass);

        try (JarOutputStream jos = new JarOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputJar)), manifest)) {
            if (loaderJar != null && Files.exists(loaderJar)) {
                addJarEntries(jos, loaderJar, "", true);
            }
            if (appClassesDir != null && Files.isDirectory(appClassesDir)) {
                addDirectory(jos, appClassesDir, CLASSES_PREFIX);
            }
            for (Path lib : libJars) {
                if (!Files.exists(lib)) {
                    continue;
                }
                addStoredJar(jos, lib, LIB_PREFIX + lib.getFileName());
            }
        }
    }

    /**
     * 将指定 jar 中的条目拷贝到输出流，可按需跳过 META-INF 及 module-info 等条目。
     *
     * @param jos         目标输出流
     * @param jar         源 jar
     * @param prefix      条目名前缀
     * @param skipMetaInf 是否跳过 META-INF 条目
     * @throws IOException 读取或写入条目时发生 I/O 错误
     */
    private static void addJarEntries(JarOutputStream jos, Path jar, String prefix, boolean skipMetaInf)
            throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.equals("module-info.class") || name.equals("package-info.class")) {
                    continue;
                }
                if (skipMetaInf && name.startsWith("META-INF/")) {
                    continue;
                }
                JarEntry out = new JarEntry(prefix + name);
                jos.putNextEntry(out);
                try (InputStream in = jf.getInputStream(entry)) {
                    in.transferTo(jos);
                }
                jos.closeEntry();
            }
        }
    }

    private static void addDirectory(JarOutputStream jos, Path dir, String prefix) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String rel = dir.relativize(p).toString().replace(File.separatorChar, '/');
                String entryName = prefix + rel;
                try {
                    jos.putNextEntry(new JarEntry(entryName));
                    Files.copy(p, jos);
                    jos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void addStoredJar(JarOutputStream jos, Path jar, String entryName) throws IOException {
        JarEntry entry = new JarEntry(entryName);
        jos.putNextEntry(entry);
        Files.copy(jar, jos);
        jos.closeEntry();
    }
}
