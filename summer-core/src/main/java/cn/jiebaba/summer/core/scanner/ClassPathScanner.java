package cn.jiebaba.summer.core.scanner;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.DirectoryStream;
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
 * Scans the running module-path and class-path for class files under the given
 * base packages. This works for JPMS ({@code java -p ... -m ...}) as well as
 * classic classpath launches because it reads the archive entries directly
 * rather than going through the module system's resource encapsulation.
 */
public final class ClassPathScanner {
    private ClassPathScanner() {}

    public static Set<Class<?>> scan(Set<String> basePackages, ClassLoader classLoader) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        if (basePackages == null || basePackages.isEmpty()) return classes;
        if (classLoader == null) classLoader = Thread.currentThread().getContextClassLoader();

        scanModules(basePackages, classLoader, classes);
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

    private static void scanModules(Set<String> basePackages, ClassLoader classLoader, Set<Class<?>> classes) {
        try {
            ModuleLayer layer = ModuleLayer.boot();
            for (Module module : layer.modules()) {
                scanModule(module, basePackages, classLoader, classes);
            }
        } catch (Exception e) {
            // module system not available; fall through to filesystem scan
        }
    }

    private static void scanModule(Module module, Set<String> basePackages, ClassLoader classLoader, Set<Class<?>> classes) {
        ModuleReference ref = module.getLayer().configuration().findModule(module.getName())
                .map(java.lang.module.ResolvedModule::reference).orElse(null);
        if (ref == null) return;
        String modName = module.getName();
        if (modName == null) return;
        try (ModuleReader reader = ref.open()) {
            for (String pkg : basePackages) {
                String pkgPath = pkg.replace('.', '/');
                reader.list().filter(name -> name.endsWith(".class"))
                        .filter(name -> !name.startsWith("module-info") && !name.startsWith("package-info"))
                        .filter(name -> {
                            String pkgOf = name.indexOf('/') < 0 ? "" : name.substring(0, name.lastIndexOf('/'));
                            return pkgOf.equals(pkgPath) || pkgOf.startsWith(pkgPath + "/");
                        })
                        .forEach(name -> {
                            String className = name.replace('/', '.').substring(0, name.length() - ".class".length());
                            tryLoad(className, classLoader, classes);
                        });
            }
        } catch (IOException e) {
            // skip unreadable module
        }
    }

    private static List<Path> collectRoots() {
        List<Path> roots = new ArrayList<>();
        addModulePathRoots(roots, System.getProperty("jdk.module.path"));
        addClassPathRoots(roots, System.getProperty("java.class.path"));
        return roots;
    }

    private static void addModulePathRoots(List<Path> roots, String modulePath) {
        if (modulePath == null || modulePath.isBlank()) return;
        for (String entry : modulePath.split(File.pathSeparator)) {
            Path p = Paths.get(entry);
            if (Files.isDirectory(p)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                    for (Path child : ds) {
                        if (Files.isDirectory(child)) {
                            roots.add(child);
                        } else if (child.toString().endsWith(".jar")) {
                            roots.add(child);
                        }
                    }
                } catch (IOException e) {
                    // skip unreadable entry
                }
            } else if (Files.exists(p)) {
                roots.add(p);
            }
        }
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
