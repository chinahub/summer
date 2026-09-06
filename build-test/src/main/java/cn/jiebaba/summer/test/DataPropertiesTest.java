package cn.jiebaba.summer.test;

import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.data.support.DataProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/** keepalive 探活 SQL 的方言兜底测试：Oracle 默认改写为 FROM DUAL，显式配置优先。 */
public class DataPropertiesTest {

    /** 屏蔽真实 classpath 配置文件的可测试 Environment。 */
    private static final class EmptyEnv extends Environment {
        EmptyEnv() {
            super(new String[0], Map.of());
        }

        @Override
        protected String readClasspathText(String location) {
            return null;
        }
    }

    @AfterEach
    void cleanup() {
        System.getProperties().keySet().stream()
                .map(Object::toString)
                .filter(n -> n.startsWith("summer.datasource"))
                .toList()
                .forEach(n -> System.clearProperty(n));
    }

    @Test
    public void oracleUrlDefaultsKeepaliveToDual() {
        System.setProperty("summer.datasource.url", "jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        DataProperties props = DataProperties.from(new EmptyEnv());
        Assertions.assertEquals("SELECT 1 FROM DUAL", props.keepaliveQuery());
    }

    @Test
    public void nonOracleKeepsSelectOne() {
        System.setProperty("summer.datasource.url", "jdbc:sqlserver://localhost:1433;encrypt=false;databaseName=tempdb");
        DataProperties props = DataProperties.from(new EmptyEnv());
        Assertions.assertEquals("SELECT 1", props.keepaliveQuery());
    }

    @Test
    public void explicitKeepaliveQueryAlwaysWins() {
        System.setProperty("summer.datasource.url", "jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        System.setProperty("summer.datasource.keepalive-query", "SELECT 1 FROM dual");
        DataProperties props = DataProperties.from(new EmptyEnv());
        Assertions.assertEquals("SELECT 1 FROM dual", props.keepaliveQuery(), "显式配置不应被方言兜底覆盖");
    }
}
