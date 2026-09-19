package cn.jiebaba.summer.office.excel;

/**
 * Excel 公式单元格值：写入时生成 {@code <f>} 公式标签，并可选附带 {@code <v>} 缓存值。
 * <p>formula 为公式文本，建议不含前导等号（如 {@code SUM(A1:A3)}）；若传入以等号开头会自动去除。
 * cachedValue 为可选缓存结果，类型可为 {@link Number}、{@link Boolean}、{@link ErrorValue} 或字符串，
 * 供不支持公式重算的读取器直接显示；为 {@code null} 时不写出 {@code <v>} 标签。
 *
 * <pre>{@code
 * writer.writeRow(new Formula("SUM(A1:A3)", 6));
 * writer.writeRow(new Formula("NOW()"));          // 无缓存值
 * }</pre>
 */
public record Formula(String formula, Object cachedValue) {

    /** 构造仅含公式文本、无缓存值的公式单元格。 */
    public Formula(String formula) {
        this(formula, null);
    }

    /** 校验公式文本非空，并去除可能的前导等号。 */
    public Formula {
        if (formula == null || formula.isBlank()) {
            throw new IllegalArgumentException("公式文本不能为空");
        }
        if (formula.startsWith("=")) {
            formula = formula.substring(1);
        }
    }
}
