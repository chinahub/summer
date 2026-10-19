package cn.jiebaba.summer.test;

import cn.jiebaba.summer.data.metadata.TableFieldInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigInteger;

/**
 * 验证 {@link TableFieldInfo#setValue} 的数字主键窄化兜底
 * （框架缺陷：MySQL BIGINT UNSIGNED 自增主键的 generated key 是 BigInteger，
 * 直接 field.set 到 Integer/Long 字段会 IllegalArgumentException）。
 */
public class TableFieldInfoSetValueTest {

    /** 测试实体：覆盖包装类型与原始类型的整数字段 */
    static class Entity {
        private Integer id;
        private int primitiveId;
        private Long lid;
        private long primitiveLid;
        private String name;
    }

    private static TableFieldInfo fieldInfo(String property) throws Exception {
        Field f = Entity.class.getDeclaredField(property);
        return new TableFieldInfo(property, property, f, true, true, false, false, null, null, null);
    }

    @Test
    void bigIntegerToIntegerField() throws Exception {
        Entity e = new Entity();
        fieldInfo("id").setValue(e, new BigInteger("42"));
        Assertions.assertEquals(42, e.id, "BigInteger 应回填为 Integer");
    }

    @Test
    void bigIntegerToLongField() throws Exception {
        Entity e = new Entity();
        fieldInfo("lid").setValue(e, new BigInteger("1099511627776"));
        Assertions.assertEquals(1099511627776L, e.lid, "BigInteger 应回填为 Long");
    }

    @Test
    void bigIntegerToPrimitiveIntField() throws Exception {
        Entity e = new Entity();
        fieldInfo("primitiveId").setValue(e, new BigInteger("7"));
        Assertions.assertEquals(7, e.primitiveId, "BigInteger 应回填为 int");
    }

    @Test
    void bigIntegerToPrimitiveLongField() throws Exception {
        Entity e = new Entity();
        fieldInfo("primitiveLid").setValue(e, new BigInteger("99"));
        Assertions.assertEquals(99L, e.primitiveLid, "BigInteger 应回填为 long");
    }

    @Test
    void intBoundaryValuesKept() throws Exception {
        Entity e = new Entity();
        TableFieldInfo info = fieldInfo("id");
        info.setValue(e, BigInteger.valueOf(Integer.MAX_VALUE));
        Assertions.assertEquals(Integer.MAX_VALUE, e.id, "Integer.MAX_VALUE 边界应放行");
        info.setValue(e, BigInteger.valueOf(Integer.MIN_VALUE));
        Assertions.assertEquals(Integer.MIN_VALUE, e.id, "Integer.MIN_VALUE 边界应放行");
    }

    @Test
    void bigIntegerOutOfIntRangeRejected() throws Exception {
        Entity e = new Entity();
        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                () -> fieldInfo("id").setValue(e, BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)),
                "超出 int 范围的 generated key 应 fail-fast");
        Assertions.assertTrue(ex.getMessage().contains("out of int range"));
    }

    @Test
    void alreadyAssignableNumberPassthrough() throws Exception {
        Entity e = new Entity();
        fieldInfo("lid").setValue(e, 123L);
        Assertions.assertEquals(123L, e.lid, "Long 值写 Long 字段不受窄化影响");
        fieldInfo("id").setValue(e, 5);
        Assertions.assertEquals(5, e.id, "Integer 值写 Integer 字段不受窄化影响");
    }

    @Test
    void nonNumberValueUntouched() throws Exception {
        Entity e = new Entity();
        fieldInfo("name").setValue(e, "summer");
        Assertions.assertEquals("summer", e.name, "非 Number 值走原路径");
    }
}
