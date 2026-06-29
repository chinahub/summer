package cn.jiebaba.summer.core.scanner;

import java.io.File;
import java.io.IOException;
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
 * Scans the class-path for class files under the given base packages,
 * reading directory and jar entries directly from {@code java.class.path}.
 *
 * <p><b>Note:</b> only the application class path ({@code java.class.path})
 * is scanned. On JDK 9+ the platform/JDK classes live on the module path and
 * are never enumerated here, so JDK internals are not traversed.
 *
 * <p>Jar scanning is optimized with an O(1) probe: a jar that has no entry
 * for the requested package directory is skipped without iterating its entries,
 * so unrelated dependencies (drivers, libs) are not walked.
 */
public final class ClassPathScanner {
    private ClassPathScanner() {}

    public static Set<Class<?>> scan(Set<String> basePackages, ClassLoader classLoader) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        if (basePackages == null || basePackages.isEmpty()) return classes;
        if (classLoader == null) classLoader = Thread.currentThread().getContextClassLoader();

        for (Path root : collectRoots()) {
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

    private static List<Path> collectRoots() {
        List<Path> roots = new ArrayList<>();
        addClassPathRoots(roots, System.getProperty("java.class.path"));
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
            // ignore unreadable directory
        }
    }

    private static void scanJar(Path jarPath, String pkgPath, String pkg, ClassLoader classLoader, Set<Class<?>> classes) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Fast path: a single O(1) probe for the package directory entry. If the jar has no
            // entry under pkgPath/ it cannot contain any class in the target package, so skip
            // iterating its (potentially thousands of) entries entirely. This keeps unrelated
            // jars (e.g. JDBC drivers, JSON libs) from being walked when scanning app packages.
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
            // skip unreadable jar
        }
    }

    private static void tryLoad(String className, ClassLoader classLoader, Set<Class<?>> classes) {
        if (className.indexOf('$') >= 0) return; // skip nested classes
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            classes.add(clazz);
        } catch (LinkageError | ClassNotFoundException e) {
            // skip classes we cannot load (optional deps etc.)
        }
    }
}
