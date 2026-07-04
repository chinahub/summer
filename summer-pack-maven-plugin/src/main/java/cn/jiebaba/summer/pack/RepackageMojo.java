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
 * 将 summer 应用重新打包为 Spring Boot 风格的可执行 jar：依赖以嵌套 jar 形式存放在
 * {@code BOOT-INF/lib} 下，应用类存放在 {@code BOOT-INF/classes} 下。
 *
 * <p>绑定到 {@code package} 阶段；默认在原始瘦 jar 旁生成一个独立的
 * {@code <finalName>-boot.jar}，保持主构件（{@code <finalName>.jar}）不变，
 * 使其仍可被其他模块作为依赖使用（与 {@code spring-boot-maven-plugin} 一致，
 * 后者将重新打包的归档作为独立的 classifier 构件）。将
 * {@link #setClassifier(String) classifier} 置空则改为替换主构件。
 *
 * <p>启动器类（{@code summer-boot-loader}，即 {@code JarLauncher}）取自
 * <em>插件自身的 classpath</em>——插件声明 {@code summer-boot-loader} 为依赖——
 * 并写入 jar 根目录作为 {@code Main-Class}。因此应用工程 <strong>无需</strong>
 * 声明 {@code summer-boot-loader}；这与 {@code spring-boot-maven-plugin} 在无需
 * 应用 classpath 上提供 {@code spring-boot-loader} 的做法一致。
 *
 * <p>无注解：字段注入由手写的 {@code plugin.xml} 描述符驱动，因此无需
 * {@code maven-plugin-annotations} / {@code maven-plugin-plugin} 即可构建插件。
 */
public class RepackageMojo extends AbstractMojo {

    /** 正在构建的 Maven 工程（注入）。 */
    private MavenProject project;

    /** 写入清单 Main-Class 的启动器主类。 */
    private String mainClass = "cn.jiebaba.summer.loader.JarLauncher";

    /** 写入清单 Start-Class 的应用启动类。 */
    private String startClass;

    /**
     * 追加到重新打包 jar 名称后的 classifier。默认为 {@code boot}，生成独立的
     * {@code <finalName>-boot.jar}，并保持主构件（瘦 jar）不变以便其他模块作为依赖使用。
     * 为空时，重新打包的可执行 jar 替换主构件（{@code <finalName>.jar}），原始瘦 jar
     * 备份为 {@code <finalName>.jar.original}。
     */
    private String classifier = "boot";

    /** 用于在插件 classpath 上定位 loader jar 的资源条目。 */
    private static final String LOADER_ENTRY = "cn/jiebaba/summer/loader/JarLauncher.class";

    @Override
    /**
     * 执行重新打包：解析输出 jar 路径与运行时 classpath，按需备份原始瘦 jar，
     * 定位 loader jar，收集依赖 jar，最终调用 {@link BootJarBuilder} 构建可执行 jar。
     *
     * @throws MojoExecutionException 当缺少 startClass、解析 classpath 失败或构建 jar 失败时抛出
     */
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

        // 替换主构件时，先备份原始瘦 jar（spring-boot-maven-plugin 约定：<finalName>.jar.original），
        // 使最终只保留一个 <finalName>.jar——即可执行的那一个。
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

        // 启动器取自插件自身的 classpath，不会重复放入 lib。
        Path loaderJar = findLoaderJar();

        List<Path> libJars = new ArrayList<>();
        for (String element : classpath) {
            if (!element.endsWith(".jar")) {
                continue;
            }
            String fileName = Path.of(element).getFileName().toString();
            // 启动器位于 jar 根目录，不再嵌套放入 BOOT-INF/lib。
            // 同时覆盖新名（summer-boot-loader）与旧名（summer-loader），避免仍声明
            // loader 依赖的工程出现重复。
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
     * 通过解析 {@code JarLauncher} 类资源，在本插件自身的 classpath 上定位
     * {@code summer-boot-loader} jar。插件声明 {@code summer-boot-loader} 为依赖，
     * 因此无论应用的依赖如何，该 jar 都存在于插件 realm 中。
     *
     * @throws MojoExecutionException 当 loader 缺失或未被打包为 jar 时抛出
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
            // getJarFileURL() 只是对已解析的 jar: URL 的纯取值方法，不会打开或锁定文件；
            // BootJarBuilder 会通过 try-with-resources 重新打开它以进行读取。
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
