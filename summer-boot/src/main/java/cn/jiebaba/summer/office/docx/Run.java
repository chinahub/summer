package cn.jiebaba.summer.office.docx;

/**
 * DOCX 文本运行：一段具有相同样式的连续文本，对应 OOXML 的 {@code <w:r>}。
 * <p>支持加粗、斜体、下划线、字号（半磅，0 表示继承）、颜色（十六进制如 {@code FF0000}）。
 * 通过静态工厂与链式方法构造：
 * <pre>{@code
 * Run r = Run.text("Hello").withBold().withSize(14).withColor("FF0000");
 * Run b = Run.bold("重要");
 * }</pre>
 * <p>注意：record 自动生成的同名访问器（{@link #bold()} 等返回 boolean）与本类的链式方法
 * 签名不同会冲突，故链式方法统一加 {@code with} 前缀。
 */
public record Run(String text, boolean bold, boolean italic, boolean underline, int size, String color) {

    /** 构造纯文本运行（无样式）。 */
    public Run(String text) {
        this(text, false, false, false, 0, null);
    }

    /** 构造纯文本运行。 */
    public static Run text(String text) {
        return new Run(text);
    }

    /** 构造加粗文本运行。 */
    public static Run bold(String text) {
        return new Run(text, true, false, false, 0, null);
    }

    /** 构造斜体文本运行。 */
    public static Run italic(String text) {
        return new Run(text, false, true, false, 0, null);
    }

    /** 返回加粗的副本。 */
    public Run withBold() {
        return new Run(text, true, italic, underline, size, color);
    }

    /** 返回斜体的副本。 */
    public Run withItalic() {
        return new Run(text, bold, true, underline, size, color);
    }

    /** 返回下划线的副本。 */
    public Run withUnderline() {
        return new Run(text, bold, italic, true, size, color);
    }

    /** 返回指定字号（磅）的副本；内部以半磅存储。 */
    public Run withSize(int points) {
        return new Run(text, bold, italic, underline, points * 2, color);
    }

    /** 返回指定颜色（十六进制 RGB，如 "FF0000"）的副本。 */
    public Run withColor(String hex) {
        return new Run(text, bold, italic, underline, size, hex);
    }
}
