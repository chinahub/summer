package cn.jiebaba.summer.pack;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Repackages a summer application into a Spring Boot-style executable jar whose
 * dependencies are kept as nested jars under {@code BOOT-INF/lib} and the
 * application classes live under {@code BOOT-INF/classes}.
 *
 * <p>Bound to the {@code package} phase; by default produces a separate
 * {@code <finalName>-boot.jar} alongside the original thin jar, leaving the main
 * artifact ({@code <finalName>.jar}) intact so it remains usable as a dependency by
 * other modules (mirroring {@code spring-boot-maven-plugin}, which keeps the repackaged
 * archive as a separate classifier artifact). Set {@link #setClassifier(String)
 * classifier} to blank to replace the main artifact instead.
 *
 * <p>The launcher classes ({@code summer-boot-loader}, i.e. {@code JarLauncher}) are
 * sourced from <em>this plugin's own classpath</em> — the plugin declares
 * {@code summer-boot-loader} as a dependency — and written to the jar root as
 * {@code Main-Class}. Application projects therefore do <strong>not</strong> need to
 * declare {@code summer-boot-loader}; this mirrors how {@code spring-boot-maven-plugin}
 * bundles {@code spring-boot-loader} without requiring it on the application classpath.
 *
 * <p>Annotation-free: field injection is driven by the hand-written
 * {@code plugin.xml} descriptor so the plugin builds without
 * {@code maven-plugin-annotations} / {@code maven-plugin-plugin}.
 */
public class RepackageMojo extends AbstractMojo {

    /** The Maven project being built (injected). */
    private MavenProject project;

    /** Launcher main class written as Main-Class in the manifest. */
    private String mainClass = "cn.jiebaba.summer.loader.JarLauncher";

    /** Application start class written as Start-Class in the manifest. */
    private String startClass;

    /**
     * Classifier appended to the repackaged jar name. Defaults to {@code boot}, producing
     * a separate {@code <finalName>-boot.jar} and leaving the main artifact (the thin jar)
     * intact for use as a dependency by other modules. When blank, the repackaged
     * executable jar replaces the main artifact ({@code <finalName>.jar}) and the original
     * thin jar is backed up as {@code <finalName>.jar.original}.
     */
    private String classifier = "boot";

    /** Resource entry used to locate the loader jar on the plugin classpath. */
    private static final String LOADER_ENTRY = "cn/jiebaba/summer/loader/JarLauncher.class";

    @Override
    public void execute() throws MojoExecutionException {
        if (startClass == null || startClass.isBlank()) {
            throw new MojoExecutionException(
                    "startClass is required (e.g. cn.jiebaba.summer.sample.Application)");
        }

        String outputDir = project.getBuild().getOutputDirectory();
        String buildDir = project.getBuild().getDirectory();
        String finalName = project.getBuild().getFinalName();
        boolean replace = classifier == null || classifier.isBlank();
        Path outputJar = Path.of(buildDir,
                replace ? finalName + ".jar" : finalName + "-" + classifier + ".jar");

        List<String> classpath;
        try {
            classpath = project.getRuntimeClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed to resolve runtime classpath", e);
        }

        // When replacing the main artifact, back up the original thin jar first
        // (spring-boot-maven-plugin convention: <finalName>.jar.original), so only one
        // <finalName>.jar remains — the executable one.
        if (replace) {
            Path original = Path.of(buildDir, finalName + ".jar");
            if (Files.exists(original)) {
                Path backup = Path.of(buildDir, finalName + ".jar.original");
                try {
                    Files.deleteIfExists(backup);
                    Files.move(original, backup);
                } catch (Exception e) {
                    throw new MojoExecutionException(
                            "Failed to back up original jar " + original + ": " + e.getMessage(), e);
                }
            }
        }

        Path appClassesDir = Path.of(outputDir);

        // The launcher is bundled from the plugin's own classpath, never duplicated into lib.
        Path loaderJar = findLoaderJar();

        List<Path> libJars = new ArrayList<>();
        for (String element : classpath) {
            if (!element.endsWith(".jar")) {
                continue;
            }
            String fileName = Path.of(element).getFileName().toString();
            // The launcher lives at the jar root; never also nest it under BOOT-INF/lib.
            // Covers both the new (summer-boot-loader) and legacy (summer-loader) names so
            // projects that still declare a loader dependency are not duplicated.
            if (fileName.startsWith("summer-boot-loader-") || fileName.startsWith("summer-loader-")) {
                continue;
            }
            libJars.add(Path.of(element));
        }

        getLog().info("Repackaging " + finalName + " into " + outputJar.getFileName()
                + " (" + libJars.size() + " libs, startClass=" + startClass + ")");
        try {
            BootJarBuilder.build(outputJar, appClassesDir, libJars, loaderJar, mainClass, startClass);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build executable jar", e);
        }
        getLog().info("Built executable jar: " + outputJar);
    }

    /**
     * Locates the {@code summer-boot-loader} jar on this plugin's own classpath by resolving
     * the {@code JarLauncher} class resource. The plugin declares {@code summer-boot-loader}
     * as a dependency, so it is present in the plugin realm regardless of the application's
     * dependencies.
     *
     * @throws MojoExecutionException if the loader is absent or not packaged as a jar
     */
    private Path findLoaderJar() throws MojoExecutionException {
        URL resource = getClass().getClassLoader().getResource(LOADER_ENTRY);
        if (resource == null) {
            throw new MojoExecutionException(
                    "summer-boot-loader not found on the plugin classpath; the plugin must"
                            + " declare summer-boot-loader as a dependency.");
        }
        if (!"jar".equals(resource.getProtocol())) {
            throw new MojoExecutionException(
                    "Expected summer-boot-loader classes to be inside a jar, but found: " + resource);
        }
        try {
            // getJarFileURL() is a pure getter on the parsed jar: URL and does not open/lock
            // the file; BootJarBuilder reopens it via try-with-resources for reading.
            JarURLConnection conn = (JarURLConnection) resource.openConnection();
            URL jarUrl = conn.getJarFileURL();
            return Paths.get(jarUrl.toURI());
        } catch (Exception e) {
            throw new MojoExecutionException(
                    "Failed to locate summer-boot-loader jar: " + e.getMessage(), e);
        }
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public void setStartClass(String startClass) {
        this.startClass = startClass;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }
}