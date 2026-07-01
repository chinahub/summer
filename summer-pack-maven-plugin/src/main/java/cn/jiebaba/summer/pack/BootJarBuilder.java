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
 * Assembles a Spring Boot-style executable jar with nested layout:
 * <pre>
 *   &lt;root&gt;/cn/jiebaba/summer/loader/JarLauncher.class   launcher (from summer-boot-loader, bundled by the plugin)
 *   BOOT-INF/classes/...                                  application classes &amp; resources
 *   BOOT-INF/lib/*.jar                                    dependency jars (unexploded)
 *   META-INF/MANIFEST.MF                                  Main-Class / Start-Class
 * </pre>
 *
 * <p>Pure JDK (java.util.jar) so it needs no third-party dependencies and can run
 * inside a Maven plugin. Entry names use forward slashes and the manifest is
 * written first by {@link JarOutputStream}, satisfying {@code java -jar}.
 */
final class BootJarBuilder {

    private static final String CLASSES_PREFIX = "BOOT-INF/classes/";
    private static final String LIB_PREFIX = "BOOT-INF/lib/";

    private BootJarBuilder() {
    }

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