package cn.jiebaba.summer.boot.ai.vectorstore;

import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.support.TypeHandler;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * pgvector 向量类型处理器：将 Java float[] 与 pgvector 文本字面量 [v1,v2,...] 互转。
 * 写入时以字面量字符串绑定，配合 SQL 中的 {@code ?::vector} 显式转换；
 * 读取时解析向量列的文本表示回 float[]。零第三方依赖，不依赖 pgvector JDBC 类型。
 * 作为 summer-data {@link TypeHandler} 扩展点的一个实现，供 JdbcVectorStore 复用。
 */
public class VectorTypeHandler implements TypeHandler {

    public static final VectorTypeHandler INSTANCE = new VectorTypeHandler();

    @Override
    public void setParameter(PreparedStatement ps, int index, Object value, Dialect dialect) throws SQLException {
        ps.setString(index, toLiteral((float[]) value));
    }

    @Override
    public Object getResult(ResultSet rs, int index, Class<?> javaType, Dialect dialect) throws SQLException {
        return parse(rs.getString(index));
    }

    /** float[] 转 pgvector 文本字面量，固定 8 位小数避免科学计数法。 */
    public static String toLiteral(float[] vec) {
        if (vec == null || vec.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder(vec.length * 12);
        sb.append('[');
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.8f", (double) vec[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    /** pgvector 文本字面量 [v1,v2,...] 转 float[]。 */
    public static float[] parse(String literal) {
        if (literal == null) {
            return null;
        }
        String s = literal.trim();
        if (s.startsWith("[")) {
            s = s.substring(1);
        }
        if (s.endsWith("]")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.isBlank()) {
            return new float[0];
        }
        String[] parts = s.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }
}
