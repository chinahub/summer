package cn.jiebaba.summer.core.scanner;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 扫描 class-path 中位于给定基础包下的 class 文件，直接读取目录与 jar 条目（来源为
 * {@code java.class.path} 及类加载器的 URL）。
 *
 * <p><b>注意：</b>默认仅扫描应用 class path（{@code java.class.path}）。在 JDK 9+ 上，
 * 平台/JDK 类位于 module path，不会被此处枚举，因此不会遍历 JDK 内部类。
 * 当类加载器为 {@link URLClassLoader}（如 surefire forkCount=0 的隔离类加载器，
 * 此时 {@code java.class.path} 只含宿主进程路径）时，一并扫描其 URL 中的目录与 jar。
 *
 * <p>jar 扫描做了 O(1) 探测优化：若某个 jar 不含所请求包目录对应的条目，则跳过它而
 * 不遍历其条目，从而不会扫描无关依赖（驱动、库等）。
 */
public final class ClassPathScanner {
    private ClassPathScanner() {}

    public static Set<Class<?>> scan(Set<String> basePackages, ClassLoader classLoader) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        if (basePackages == null || basePackages.isEmpty()) return classes;
        if (classLoader == null) classLoader = Thread.currentThread().getContextClassLoader();

        for (Path root : collectRoots(classLoader)) {
            for (String pkg : basePackages) {
                String path = pkg.replace('.', '/');
                if (Files.isDirectory(root)) {
                    scanDirectory(root.resolve(path), pkg, classLoader, classes);
                } else if (root.toString().endsWith(".jar") && Files.exists(root)) {
                    scanJar(root, path, pkg, classLoader, classes);
                }
            }
        }
        return classes;
    }

    /** 收集扫描根：java.class.path 全部条目；若类加载器为 URLClassLoader，再并入其 file URL。 */
    private static List<Path> collectRoots(ClassLoader classLoader) {
        List<Path> roots = new ArrayList<>();
        addClassPathRoots(roots, System.getProperty("java.class.path"));
        // 隔离类加载器环境（如 surefire 进程内执行）：java.class.path 只含宿主进程路径，
        // 真实测试类路径挂在 URLClassLoader 的 URLs 上，需一并扫描
        if (classLoader instanceof URLClassLoader ucl) {
            for (URL url : ucl.getURLs()) {
                if (!"file".equals(url.getProtocol())) continue;
                try {
                    Path p = Path.of(url.toURI());
                    if (Files.exists(p) && !roots.contains(p)) roots.add(p);
                } catch (URISyntaxException | IllegalArgumentException e) {
                    // 跳过无法转换为路径的 URL
                }
            }
        }
        return roots;
    }

    private static void addClassPathRoots(List<Path> roots, String classPath) {
        if (classPath == null || classPath.isBlank()) return;
        for (String entry : classPath.split(File.pathSeparator)) {
            Path p = Paths.get(entry);
            if (Files.exists(p)) roots.add(p);
        }
    }

    private static void scanDirectory(Path dir, String pkg, ClassLoader classLoader, Set<Class<?>> classes) {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".class"))
                  .forEach(p -> {
                      Path rel = dir.relativize(p);
                      String className = rel.toString().replace(File.separatorChar, '.');
                      className = className.substring(0, className.length() - ".class".length());
                      String fullName = pkg.isEmpty() ? className : pkg + "." + className;
                      tryLoad(fullName, classLoader, classes);
                  });
        } catch (IOException e) {
            // 忽略不可读目录
        }
    }

    /**
     * 扫描 jar 内指定包路径下的类，对包目录做 O(1) 探测以跳过无关 jar。
     */
    private static void scanJar(Path jarPath, String pkgPath, String pkg, ClassLoader classLoader, Set<Class<?>> classes) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // 快速路径：对包目录条目做一次 O(1) 探测。若 jar 中不存在 pkgPath/ 下的条目，
            // 则它不可能包含目标包中的任何类，故整体跳过其（可能数以千计的）条目遍历。
            // 这避免在扫描应用包时遍历无关 jar（如 JDBC 驱动、JSON 库）。
            if (!pkgPath.isEmpty() && jar.getJarEntry(pkgPath + "/") == null) return;
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class")) continue;
                if (name.startsWith("module-info") || name.startsWith("package-info")) continue;
                String asPkg = name.indexOf('/') < 0 ? "" : name.substring(0, name.lastIndexOf('/')).replace('/', '.');
                if (!asPkg.equals(pkg) && !asPkg.startsWith(pkg + ".")) continue;
                String className = name.replace('/', '.').substring(0, name.length() - ".class".length());
                tryLoad(className, classLoader, classes);
            }
        } catch (IOException e) {
            // 跳过不可读 jar
        }
    }

    private static void tryLoad(String className, ClassLoader classLoader, Set<Class<?>> classes) {
        if (className.indexOf('$') >= 0) return; // 跳过嵌套类
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            classes.add(clazz);
        } catch (LinkageError | ClassNotFoundException e) {
            // 跳过无法加载的类（可选依赖等）
        }
    }
}
