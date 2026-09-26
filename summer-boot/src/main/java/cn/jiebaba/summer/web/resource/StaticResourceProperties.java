package cn.jiebaba.summer.web.resource;

import cn.jiebaba.summer.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * 静态资源配置（{@code summer.web.static.*}）：
 * <ul>
 *   <li>{@code summer.web.static.enabled}：是否启用（默认 true，未打包静态资源时零影响）；</li>
 *   <li>{@code summer.web.static.locations}：资源根目录列表（逗号分隔，按顺序查找；
 *       支持 {@code classpath:} 与文件系统路径，默认
 *       classpath:/static,classpath:/public,classpath:/resources,classpath:/META-INF/resources）；</li>
 *   <li>{@code summer.web.static.welcome}：欢迎页文件名（默认 index.html，映射路径 /）。</li>
 * </ul>
 */
public final class StaticResourceProperties {

    private final boolean enabled;
    private final List<String> locations;
    private final String welcome;

    public StaticResourceProperties(boolean enabled, List<String> locations, String welcome) {
        this.enabled = enabled;
        this.locations = List.copyOf(locations);
        this.welcome = welcome == null || welcome.isBlank() ? "index.html" : welcome;
    }

    public static StaticResourceProperties from(Environment env) {
        boolean enabled = Boolean.parseBoolean(env.getProperty("summer.web.static.enabled", "true"));
        String raw = env.getProperty("summer.web.static.locations",
                "classpath:/static,classpath:/public,classpath:/resources,classpath:/META-INF/resources");
        List<String> locations = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) locations.add(trimmed);
        }
        String welcome = env.getProperty("summer.web.static.welcome", "index.html");
        return new StaticResourceProperties(enabled, locations, welcome);
    }

    public boolean enabled() { return enabled; }
    public List<String> locations() { return locations; }
    public String welcome() { return welcome; }
}
