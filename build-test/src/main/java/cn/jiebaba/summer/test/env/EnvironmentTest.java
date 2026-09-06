package cn.jiebaba.summer.test.env;

import cn.jiebaba.summer.core.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Environment 外部化配置的单测：profile 叠加、YAML 多文档 on-profile 条件段、
 * 命令行参数覆盖、环境变量按需匹配（不再全量拷贝污染配置）与优先级次序。
 * 通过 {@code readClasspathText} 覆盖注入假文件内容，与真实 classpath 完全隔离。
 */
public class EnvironmentTest {

    /** 测试文件的静态注入表：构造器内读取，须在子类构造前就绪，故为 static。 */
    private static final Map<String, String> FILES = new ConcurrentHashMap<>();

    /** 以注入文件内容构造的可测试 Environment（屏蔽真实 classpath 的 application 配置）。 */
    private static final class TestEnv extends Environment {
        TestEnv(String[] args, Map<String, String> env) {
            super(args, env);
        }

        @Override
        protected String readClasspathText(String location) {
            return FILES.get(location);
        }
    }

    @AfterEach
    void cleanup() {
        FILES.clear();
        System.getProperties().keySet().stream()
                .map(Object::toString)
                .filter(n -> n.startsWith("summer.test."))
                .toList()
                .forEach(n -> System.clearProperty(n));
    }

    @Test
    public void profileFileOverridesBaseFile() {
        FILES.put("application.yml", "summer.profiles.active: prod\nsummer.test.db.url: base-url\n");
        FILES.put("application-prod.yml", "summer.test.db.url: prod-url\nsummer.test.db.extra: x\n");
        Environment env = new TestEnv(new String[0], Map.of());
        Assertions.assertEquals(Set.of("prod"), env.getActiveProfiles());
        Assertions.assertEquals("prod-url", env.getProperty("summer.test.db.url"));
        Assertions.assertEquals("x", env.getProperty("summer.test.db.extra"));
    }

    @Test
    public void commandLineActivatesProfileAndOverridesFile() {
        FILES.put("application.yml", "summer.test.db.url: base-url\nsummer.test.port: 8080\n");
        FILES.put("application-dev.yml", "summer.test.db.url: dev-url\n");
        Environment env = new TestEnv(
                new String[]{"--summer.profiles.active=dev", "--summer.test.port=9090"}, Map.of());
        Assertions.assertEquals(Set.of("dev"), env.getActiveProfiles());
        Assertions.assertEquals("dev-url", env.getProperty("summer.test.db.url"));
        Assertions.assertEquals("9090", env.getProperty("summer.test.port"));
    }

    @Test
    public void laterProfileWins() {
        FILES.put("application-base.yml", "summer.test.val: from-base\nsummer.test.only: b\n");
        FILES.put("application-overlay.yml", "summer.test.val: from-overlay\n");
        Environment env = new TestEnv(new String[]{"--summer.profiles.active=base,overlay"}, Map.of());
        Assertions.assertEquals("from-overlay", env.getProperty("summer.test.val"));
        Assertions.assertEquals("b", env.getProperty("summer.test.only"));
    }

    @Test
    public void yamlOnProfileDocumentFiltered() {
        FILES.put("application.yml", """
                summer.profiles.active: dev
                summer.test.key1: base1
                ---
                summer.config.activate.on-profile: dev
                summer.test.key1: dev1
                summer.test.key2: dev2
                ---
                summer.config.activate.on-profile: prod
                summer.test.key3: prod3
                """);
        Environment env = new TestEnv(new String[0], Map.of());
        Assertions.assertEquals("dev1", env.getProperty("summer.test.key1"));
        Assertions.assertEquals("dev2", env.getProperty("summer.test.key2"));
        Assertions.assertNull(env.getProperty("summer.test.key3"));
        Assertions.assertNull(env.getProperty(Environment.ON_PROFILE_KEY));
    }

    @Test
    public void envVarOverridesFileOnDemand() {
        FILES.put("application.yml", "summer.test.token: from-file\n");
        Environment env = new TestEnv(new String[0], Map.of("SUMMER_TEST_TOKEN", "from-env"));
        Assertions.assertEquals("from-env", env.getProperty("summer.test.token"));
    }

    @Test
    public void envVarNotCopiedIntoProperties() {
        // 修复验证：环境变量不再全量拷贝进配置，未按需匹配的键（如 path）不可见
        Environment env = new TestEnv(new String[0], Map.of());
        Assertions.assertNull(env.getProperty("path"));
    }

    @Test
    public void allKeepsRelaxedEnvMappingForCompatibility() {
        Environment env = new TestEnv(new String[0], Map.of("PATH", "/usr/bin"));
        Assertions.assertEquals("/usr/bin", env.all().get("path"));
    }

    @Test
    public void systemPropertyBeatsEnvVarAndFile() {
        FILES.put("application.yml", "summer.test.sysorder: from-file\n");
        System.setProperty("summer.test.sysorder", "from-sys");
        try {
            Environment env = new TestEnv(new String[0], Map.of("SUMMER_TEST_SYSORDER", "from-env"));
            Assertions.assertEquals("from-sys", env.getProperty("summer.test.sysorder"));
        } finally {
            System.clearProperty("summer.test.sysorder");
        }
    }

    @Test
    public void missingKeyReturnsDefaultAndContainsWorks() {
        Environment env = new TestEnv(new String[0], Map.of());
        Assertions.assertFalse(env.containsProperty("summer.test.nope"));
        Assertions.assertEquals("dft", env.getProperty("summer.test.nope", "dft"));
    }
}
