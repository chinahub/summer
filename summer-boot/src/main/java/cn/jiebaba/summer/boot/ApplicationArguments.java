package cn.jiebaba.summer.boot;

import java.util.List;
import java.util.Set;

/**
 * Provides access to the arguments that were used to run a {@link SummerApplication}.
 * <p>Arguments are split into <em>option</em> arguments (prefixed with {@code --}, optionally
 * {@code --name=value}) and <em>non-option</em> arguments (everything else), mirroring the
 * convention used by Spring Boot's {@code ApplicationArguments}.
 */
public interface ApplicationArguments {

    /** The raw arguments as supplied to {@link SummerApplication#run}. */
    String[] getSourceArgs();

    /** The names of all option arguments (the part after {@code --} and before {@code =}). */
    Set<String> getOptionNames();

    /** Whether an option argument with the given name was supplied. */
    boolean containsOption(String name);

    /**
     * The values declared for the option argument {@code name}. An option may carry multiple
     * values ({@code --name=v1 --name=v2}); a flag-style option ({@code --name} with no
     * {@code =}) returns an empty list. Returns an empty list if the option is absent.
     */
    List<String> getOptionValues(String name);

    /** The arguments that are not option arguments (i.e. not prefixed with {@code --}). */
    List<String> getNonOptionArgs();
}