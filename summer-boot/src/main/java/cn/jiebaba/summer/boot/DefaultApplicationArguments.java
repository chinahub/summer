package cn.jiebaba.summer.boot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default {@link ApplicationArguments} implementation. Parses the raw argument array into
 * option arguments ({@code --name} / {@code --name=value}) and non-option arguments.
 */
public final class DefaultApplicationArguments implements ApplicationArguments {

    private final String[] sourceArgs;
    private final List<String> nonOptionArgs;
    private final Map<String, List<String>> optionArgs;

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
                    // bare "--" or "--=x": treat as a non-option argument
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