package cn.jiebaba.summer.admin.system.security;

/**
 * 角色数据范围枚举：与 sys_role.data_scope 的字符串编码显式映射
 * （1全部 2自定义 3本部门 4本部门及以下 5仅本人），Java/API 侧使用稳定枚举名。
 */
public enum DataScopeType {

    /** 全部数据 */
    ALL("1"),

    /** 自定义部门集合 */
    CUSTOM("2"),

    /** 本部门 */
    DEPT("3"),

    /** 本部门及以下 */
    DEPT_AND_CHILDREN("4"),

    /** 仅本人 */
    SELF("5");

    private final String code;

    DataScopeType(String code) {
        this.code = code;
    }

    /** 返回 sys_role.data_scope 存储编码。 */
    public String code() {
        return code;
    }

    /**
     * 按存储编码解析枚举；未知或空编码返回 {@code null}，
     * 由调用方按"不贡献任何权限"的拒绝默认语义处理。
     *
     * @param code sys_role.data_scope 值
     * @return 对应枚举，无法识别时为 null
     */
    public static DataScopeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DataScopeType type : values()) {
            if (type.code.equals(code.trim())) {
                return type;
            }
        }
        return null;
    }
}
