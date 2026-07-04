package cn.jiebaba.summer.boot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ApplicationArguments} 的默认实现。将原始参数数组解析为
 * 选项参数（{@code --name} / {@code --name=value}）与非选项参数。
 */
public final class DefaultApplicationArguments implements ApplicationArguments {

    private final String[] sourceArgs;
    private final List<String> nonOptionArgs;
    private final Map<String, List<String>> optionArgs;

    /**
     * 解析原始参数数组，将其拆分为选项参数与非选项参数。
     *
     * @param args 原始启动参数，为 {@code null} 时视为空数组
     */
    public DefaultApplicationArguments(String... args) {
        this.sourceArgs = args != null ? args.clone() : new String[0];
        this.nonOptionArgs = new ArrayList<>();
        this.optionArgs = new LinkedHashMap<>();
        for (String arg : this.sourceArgs) {
            if (arg == null) continue;
            if (arg.startsWith("--")) {
                String body = arg.substring(2);
                int eq = body.indexOf('=');
                String key = eq >= 0 ? body.substring(0, eq) : body;
                if (key.isEmpty()) {
                    // 裸 "--" 或 "--=x"：视为非选项参数
                    this.nonOptionArgs.add(arg);
                    continue;
                }
                List<String> values = this.optionArgs.computeIfAbsent(key, k -> new ArrayList<>());
                if (eq >= 0) {
                    values.add(body.substring(eq + 1));
                }
            } else {
                this.nonOptionArgs.add(arg);
            }
        }
    }

    @Override
    public String[] getSourceArgs() {
        return sourceArgs.clone();
    }

    @Override
    public Set<String> getOptionNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(optionArgs.keySet()));
    }

    @Override
    public boolean containsOption(String name) {
        return name != null && optionArgs.containsKey(name);
    }

    @Override
    public List<String> getOptionValues(String name) {
        List<String> values = optionArgs.get(name);
        return values == null ? List.of() : Collections.unmodifiableList(values);
    }

    @Override
    public List<String> getNonOptionArgs() {
        return Collections.unmodifiableList(nonOptionArgs);
    }
}
