package cn.jiebaba.summer.test;

import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.dialect.MySqlDialect;
import cn.jiebaba.summer.data.dialect.OracleDialect;
import cn.jiebaba.summer.data.dialect.PostgreSqlDialect;
import cn.jiebaba.summer.data.dialect.SqlServerDialect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** 方言按需转义测试：普通标识符保持裸名，保留字/非法字符才加引号（Oracle 转大写）。 */
public class DialectQuoteTest {

    @Test
    public void plainIdentifiersStayBare() {
        Dialect[] dialects = {new MySqlDialect(), new PostgreSqlDialect(), new OracleDialect(), new SqlServerDialect()};
        for (Dialect d : dialects) {
            Assertions.assertEquals("user_id", d.quote("user_id"), d.name());
            Assertions.assertEquals("t_user.name", d.quote("t_user.name"), d.name());
            Assertions.assertEquals("name", d.quote("name"), d.name());
        }
    }

    @Test
    public void reservedWordsEscaped() {
        Assertions.assertEquals("`order`", new MySqlDialect().quote("order"));
        Assertions.assertEquals("\"order\"", new PostgreSqlDialect().quote("order"));
        Assertions.assertEquals("\"LEVEL\"", new OracleDialect().quote("level"), "Oracle 转义应统一转大写");
        Assertions.assertEquals("[user]", new SqlServerDialect().quote("user"));
        Assertions.assertEquals("[row]", new SqlServerDialect().quote("row"));
    }

    @Test
    public void illegalCharactersEscaped() {
        Dialect[] dialects = {new MySqlDialect(), new PostgreSqlDialect(), new OracleDialect(), new SqlServerDialect()};
        for (Dialect d : dialects) {
            String quoted = d.quote("my-col");
            Assertions.assertNotEquals("my-col", quoted, d.name());
            // Oracle 转义时统一转大写，其余方言保持原样
            Assertions.assertTrue(quoted.toLowerCase().contains("my-col"), d.name());
            Assertions.assertNotEquals("1abc", d.quote("1abc"), "数字开头应转义: " + d.name());
        }
    }

    @Test
    public void qualifiedNameEscapedPerSegment() {
        // schema 段与表段各自判断：普通名不转义，保留字段才转义
        Assertions.assertEquals("app.t_order", new PostgreSqlDialect().quote("app.t_order"));
        Assertions.assertEquals("app.user_data", new SqlServerDialect().quote("app.user_data"),
                "user_data 非保留字，应保持裸名");
        Assertions.assertEquals("app.[user]", new SqlServerDialect().quote("app.user"));
        Assertions.assertEquals("app.\"LEVEL\"", new OracleDialect().quote("app.level"),
                "schema 段保持裸名（隐式转大写），保留字段转义转大写");
    }

    @Test
    public void quoteNeverReturnsNullOrBlanks() {
        Dialect d = new OracleDialect();
        Assertions.assertNull(d.quote(null));
        Assertions.assertEquals("", d.quote(""));
    }
}
