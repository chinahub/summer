package cn.jiebaba.summer.boot;

import java.util.List;
import java.util.Set;

/**
 * 提供对用于运行 {@link SummerApplication} 的启动参数的访问。
 * <p>参数分为 <em>选项</em> 参数（以 {@code --} 为前缀，可写作 {@code --name=value}）
 * 与 <em>非选项</em> 参数（其余参数），沿用 Spring Boot {@code ApplicationArguments} 的约定。
 */
public interface ApplicationArguments {

    /** 传递给 {@link SummerApplication#run} 的原始参数。 */
    String[] getSourceArgs();

    /** 所有选项参数的名称（{@code --} 之后、{@code =} 之前的部分）。 */
    Set<String> getOptionNames();

    /** 是否提供了指定名称的选项参数。 */
    boolean containsOption(String name);

    /**
     * 选项参数 {@code name} 所声明的值。一个选项可携带多个值
     * （{@code --name=v1 --name=v2}）；标志式选项（不带 {@code =} 的 {@code --name}）
     * 返回空列表；选项不存在时也返回空列表。
     */
    List<String> getOptionValues(String name);

    /** 非选项参数（即不以 {@code --} 为前缀的参数）。 */
    List<String> getNonOptionArgs();
}
