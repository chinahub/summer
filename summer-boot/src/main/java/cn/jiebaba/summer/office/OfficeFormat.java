package cn.jiebaba.summer.office;

/** 文档格式枚举，涵盖本模块支持的解析与生成目标。 */
public enum OfficeFormat {

    CSV,
    XLSX,
    DOCX,
    PDF,
    XML,
    MD;

    /** 根据文件名扩展名推断格式；无法识别时抛出 OfficeException。 */
    public static OfficeFormat fromExtension(String filename) {
        if (filename == null) {
            throw new OfficeException("文件名为空，无法推断格式");
        }
        String lower = filename.toLowerCase();
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            throw new OfficeException("文件名缺少扩展名：" + filename);
        }
        String ext = lower.substring(dot + 1);
        return switch (ext) {
            case "csv" -> CSV;
            case "xlsx" -> XLSX;
            case "docx" -> DOCX;
            case "pdf" -> PDF;
            case "xml" -> XML;
            case "md", "markdown" -> MD;
            default -> throw new OfficeException("不支持的文件扩展名：" + ext);
        };
    }
}
