package cn.jiebaba.summer.office.excel;

/**
 * Excel 错误单元格值：写入时生成 {@code <c t="e"><v>code</v></c>}。
 * <p>code 为 Excel 错误码，常见值：
 * {@code #DIV/0!}（除零）、{@code #VALUE!}（类型错误）、{@code #REF!}（引用无效）、
 * {@code #NAME?}（名称未识别）、{@code #N/A}（值不可用）、{@code #NUM!}（数值溢出）、{@code #NULL!}（交集为空）。
 *
 * <pre>{@code
 * writer.writeRow(new ErrorValue("#DIV/0!"));
 * }</pre>
 */
public record ErrorValue(String code) {

    /** 规范化错误码：null 时回退为 {@code #VALUE!}。 */
    public ErrorValue {
        if (code == null || code.isBlank()) {
            code = "#VALUE!";
        }
    }
}
