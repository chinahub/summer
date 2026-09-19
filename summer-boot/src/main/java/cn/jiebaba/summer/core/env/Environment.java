package cn.jiebaba.summer.core.env;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class Environment {
    public static final String DEFAULT_PROFILE = "default";

    /** 激活 profile 的配置键（逗号分隔多个）。 */
    public static final String KEY_PROFILES_ACTIVE = "summer.profiles.active";

    /** YAML 多文档中声明该文档所属 profile 的键（仅当匹配激活 profile 时该文档生效）。 */
    public static final String ON_PROFILE_KEY = "summer.config.activate.on-profile";

    /** 文件层：application 基础配置与 profile 叠加配置（低优先级）。 */
    private final Properties fileProps = new Properties();

    /** 覆盖层：系统属性与命令行参数（高优先级，后写胜出）。 */
    private final Properties overlayProps = new Properties();

    /** 环境变量快照：不预拷贝进配置，按需通过 {@link #toEnvName(String)} 映射匹配，避免键污染。 */
    private final Map<String, String> envVars;

    /** 激活的 profile 集合（保序，后面的 profile 优先级更高）。 */
    private final Set<String> activeProfiles;

    /**
     * 初始化环境：按优先级加载 application 配置（properties/yml）、profile 叠加文件、
     * 环境变量、系统属性；不处理命令行参数。
     */
    public Environment() {
        this(new String[0], System.getenv());
    }

    /**
     * 初始化环境并解析命令行参数：{@code --key=value} 形式的参数以最高优先级覆盖配置。
     *
     * @param commandLineArgs 启动参数数组
     */
    public Environment(String[] commandLineArgs) {
        this(commandLineArgs, System.getenv());
    }

    /**
     * 全量构造（可测试）：显式注入命令行参数与环境变量快照。
     * 配置合并优先级从低到高：application 基础文件 → application-{profile} 叠加文件
     * （按 profile 顺序后者覆盖前者）→ 环境变量（按需匹配）→ 系统属性 → 命令行参数。
     */
    public Environment(String[] commandLineArgs, Map<String, String> envVars) {
        this.envVars = envVars == null ? Map.of() : Map.copyOf(envVars);
        Map<String, String> cmdLine = parseCommandLine(commandLineArgs);
        // 1. 基础文件层（不区分 profile 的文档先合并，并从中提取 summer.profiles.active 兜底值）
        String baseProperties = readClasspathText("application.properties");
        String baseYml = readClasspathText("application.yml");
        Properties baseDocs = new Properties();
        if (baseYml != null) {
            mergeYaml(baseDocs, baseYml, Set.of());
        }
        this.activeProfiles = resolveActiveProfiles(cmdLine, baseDocs);
        // 2. 基础层正式合并：yml 文档按序覆盖、on-profile 段过滤；application.properties 胜出同名键
        if (baseYml != null) {
            mergeYaml(this.fileProps, baseYml, this.activeProfiles);
        }
        if (baseProperties != null) {
            loadPropertiesContent(this.fileProps, baseProperties);
        }
        // 3. profile 叠加层：逐 profile 加载 application-{profile}.properties/.yml，后者覆盖前者
        for (String profile : this.activeProfiles) {
            String pProperties = readClasspathText("application-" + profile + ".properties");
            String pYml = readClasspathText("application-" + profile + ".yml");
            Properties layer = new Properties();
            if (pYml != null) {
                mergeYaml(layer, pYml, this.activeProfiles);
            }
            if (pProperties != null) {
                loadPropertiesContent(layer, pProperties);
            }
            for (String name : layer.stringPropertyNames()) {
                this.fileProps.setProperty(name, layer.getProperty(name));
            }
        }
        // 4. 系统属性覆盖
        Properties sys = System.getProperties();
        for (String name : sys.stringPropertyNames()) {
            this.overlayProps.setProperty(name, sys.getProperty(name));
        }
        // 5. 命令行参数最高优先级
        for (Map.Entry<String, String> e : cmdLine.entrySet()) {
            this.overlayProps.setProperty(e.getKey(), e.getValue());
        }
    }

    /**
     * 解析激活的 profile 集合：命令行参数 &gt; 系统属性 &gt; 环境变量 &gt; 基础配置文件中的取值。
     * 多个 profile 逗号分隔，后面的 profile 覆盖前面的。
     */
    private Set<String> resolveActiveProfiles(Map<String, String> cmdLine, Properties baseDocs) {
        String raw = cmdLine.get(KEY_PROFILES_ACTIVE);
        if (raw == null) raw = System.getProperty(KEY_PROFILES_ACTIVE);
        if (raw == null) raw = envVars.get(toEnvName(KEY_PROFILES_ACTIVE));
        if (raw == null) raw = baseDocs.getProperty(KEY_PROFILES_ACTIVE);
        Set<String> profiles = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            for (String p : raw.split(",")) {
                if (!p.isBlank()) profiles.add(p.trim());
            }
        }
        return Collections.unmodifiableSet(profiles);
    }

    /** 解析命令行参数：仅提取 {@code --key=value} 形式的选项；重复出现时后面的覆盖前面。 */
    private static Map<String, String> parseCommandLine(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        if (args == null) return out;
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--")) continue;
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            if (eq <= 0) continue;
            out.put(body.substring(0, eq).trim(), body.substring(eq + 1));
        }
        return out;
    }

    /**
     * 解析 YAML 文本为多个文档并合并到目标 Properties：
     * 无条件文档与匹配激活 profile 的文档按文档顺序合并（后序覆盖前序），
     * 未匹配的 on-profile 文档整段跳过，{@code summer.config.activate.on-profile} 键本身不进入结果。
     */
    private void mergeYaml(Properties target, String ymlText, Set<String> activeProfiles) {
        for (Map<String, Object> doc : YamlParser.parseDocuments(ymlText)) {
            Map<String, String> flat = YamlParser.flatten(doc);
            String onProfile = flat.remove(ON_PROFILE_KEY);
            if (onProfile != null && !matchesProfiles(onProfile, activeProfiles)) {
                continue;
            }
            for (Map.Entry<String, String> e : flat.entrySet()) {
                target.setProperty(e.getKey(), e.getValue());
            }
        }
    }

    /** 判断多文档的 on-profile 值（逗号分隔）是否命中任一激活 profile。 */
    private static boolean matchesProfiles(String onProfile, Set<String> activeProfiles) {
        for (String p : onProfile.split(",")) {
            if (activeProfiles.contains(p.trim())) return true;
        }
        return false;
    }

    /** 将 .properties 文本内容逐键写入目标 Properties（直接覆盖已有键）。 */
    private static void loadPropertiesContent(Properties target, String content) {
        try {
            Properties p = new Properties();
            p.load(new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            for (String name : p.stringPropertyNames()) {
                target.setProperty(name, p.getProperty(name));
            }
        } catch (IOException ignored) {
            // 内容解析失败视为无该配置
        }
    }

    /** 读取 classpath 资源为 UTF-8 文本；不存在返回 null。子类可覆盖以注入测试内容。 */
    protected String readClasspathText(String location) {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(location)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** 配置键到环境变量名的映射：点与横杠转下划线后大写（server.port → SERVER_PORT）。 */
    private static String toEnvName(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase();
    }

    /** 三层查找：覆盖层（系统属性/命令行）→ 环境变量（按需映射）→ 文件层。 */
    private String lookup(String key) {
        String v = overlayProps.getProperty(key);
        if (v != null) return v;
        String env = envVars.get(toEnvName(key));
        if (env != null) return env;
        return fileProps.getProperty(key);
    }

    public String getProperty(String key) {
        return lookup(key);
    }

    public String getProperty(String key, String defaultValue) {
        String v = lookup(key);
        return v != null ? v : defaultValue;
    }

    public <T> T getProperty(String key, Class<T> targetType) {
        return getProperty(key, targetType, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        String value = lookup(key);
        if (value == null) return defaultValue;
        return (T) convert(value, targetType);
    }

    public boolean containsProperty(String key) {
        return lookup(key) != null;
    }

    /**
     * 返回全量配置视图（环境变量按 relaxed 规则全量映射后参与合并，保持既有行为）。
     * 合并顺序：文件层 → 环境变量 → 覆盖层（系统属性/命令行）。
     */
    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : fileProps.stringPropertyNames()) {
            map.put(name, fileProps.getProperty(name));
        }
        for (Map.Entry<String, String> e : envVars.entrySet()) {
            String key = relaxedKey(e.getKey());
            if (key != null) {
                map.put(key, e.getValue());
            }
        }
        for (String name : overlayProps.stringPropertyNames()) {
            map.put(name, overlayProps.getProperty(name));
        }
        return map;
    }

    /** 激活的 profile 集合（不可变，保序）。 */
    public Set<String> getActiveProfiles() {
        return activeProfiles;
    }

    /** 按环境变量名映射配置键：下划线转点、大写转小写（SERVER_PORT → server.port）。 */
    private static String relaxedKey(String envName) {
        if (envName == null || envName.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(envName.length() + 4);
        for (int i = 0; i < envName.length(); i++) {
            char c = envName.charAt(i);
            if (c == '_') {
                sb.append('.');
            } else if (c == '.') {
                // 保留系统风格名中的点号不变
                sb.append(c);
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** 解析给定文本中的 ${key} 与 ${key:default} 占位符。 */
    public String resolvePlaceholders(String text) {
        if (text == null) return null;
        return resolvePlaceholders(text, 0);
    }

    /**
     * 递归解析文本中的 ${key} 与 ${key:default} 占位符，最多 5 层以防循环引用。
     */
    private String resolvePlaceholders(String text, int depth) {
        // 防止循环引用导致的无限递归（最多 5 层）
        if (depth >= 5) return text;
        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                int end = text.indexOf('}', i + 2);
                if (end < 0) {
                    result.append(c);
                    i++;
                    continue;
                }
                String expr = text.substring(i + 2, end);
                String key;
                String defaultValue = null;
                int colon = expr.indexOf(':');
                if (colon >= 0) {
                    key = expr.substring(0, colon).trim();
                    defaultValue = expr.substring(colon + 1).trim();
                } else {
                    key = expr.trim();
                }
                String value = lookup(key);
                if (value == null) {
                    value = defaultValue;
                }
                if (value != null) {
                    // 递归解析值本身中的占位符以支持嵌套
                    String resolved = resolvePlaceholders(value, depth + 1);
                    result.append(resolved);
                } else {
                    result.append("${").append(expr).append("}");
                }
                i = end + 1;
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }

    /** 将原始字符串值转换为目标类型。 */
    @SuppressWarnings("unchecked")
    /**
     * 将原始字符串值转换为目标类型（基本类型、枚举、时间等）。
     */
    public static Object convert(String value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType == String.class || targetType == Object.class || targetType == CharSequence.class) {
            return value;
        }
        if (targetType == int.class || targetType == Integer.class) return Integer.decode(value);
        if (targetType == long.class || targetType == Long.class) return Long.decode(value);
        if (targetType == short.class || targetType == Short.class) return Short.decode(value);
        if (targetType == byte.class || targetType == Byte.class) return Byte.decode(value);
        if (targetType == double.class || targetType == Double.class) return Double.parseDouble(value);
        if (targetType == float.class || targetType == Float.class) return Float.parseFloat(value);
        if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(value);
        if (targetType == char.class || targetType == Character.class) {
            return value.isEmpty() ? '\0' : value.charAt(0);
        }
        if (targetType.isEnum()) {
            @SuppressWarnings("rawtypes")
            Class enumType = targetType;
            @SuppressWarnings("unchecked")
            Object constant = Enum.valueOf(enumType, value);
            return constant;
        }
        return value;
    }
}
