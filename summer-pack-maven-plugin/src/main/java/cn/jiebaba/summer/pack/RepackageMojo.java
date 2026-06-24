package cn.jiebaba.summer.pack;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Repackages a summer application into a Spring Boot-style executable jar whose
 * dependencies are kept as nested jars under {@code BOOT-INF/lib} and the
 * application classes live under {@code BOOT-INF/classes}.
 *
 * <p>Bound to the {@code package} phase; produces
 * {@code <finalName>-boot.jar} alongside the original thin jar.
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

    /** Classifier appended to the repackaged jar name. */
    private String classifier = "boot";

    /** ArtifactId of summer-loader, whose classes are placed at the jar root. */
    private String loaderArtifactId = "summer-loader";

    @Override
    public void execute() throws MojoExecutionException {
        if (startClass == null || startClass.isBlank()) {
            throw new MojoExecutionException(
                    "startClass is required (e.g. cn.jiebaba.summer.sample.Application)");
        }

        String outputDir = project.getBuild().getOutputDirectory();
        String buildDir = project.getBuild().getDirectory();
        String finalName = project.getBuild().getFinalName();
        Path outputJar = Path.of(buildDir, finalName + "-" + classifier + ".jar");

        List<String> classpath;
        try {
            classpath = project.getRuntimeClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed to resolve runtime classpath", e);
        }

        Path appClassesDir = Path.of(outputDir);
        List<Path> libJars = new ArrayList<>();
        Path loaderJar = null;
        String loaderMarker = File.separator + loaderArtifactId + "-";
        for (String element : classpath) {
            if (!element.endsWith(".jar")) {
                continue;
            }
            if (element.contains(loaderMarker)) {
                loaderJar = Path.of(element);
            } else {
                libJars.add(Path.of(element));
            }
        }
        if (loaderJar == null) {
            throw new MojoExecutionException(
                    "summer-loader jar not found on runtime classpath; "
                            + "declare summer-loader as a dependency.");
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

    public void setLoaderArtifactId(String loaderArtifactId) {
        this.loaderArtifactId = loaderArtifactId;
    }
}