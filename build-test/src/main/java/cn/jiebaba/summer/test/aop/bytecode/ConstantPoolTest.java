package cn.jiebaba.summer.test.aop.bytecode;

import cn.jiebaba.summer.core.aop.bytecode.Bytecode;
import cn.jiebaba.summer.core.aop.bytecode.ConstantPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConstantPoolTest {

    @Test
    void emptyPoolHasCountOne() {
        ConstantPool cp = new ConstantPool();
        Assertions.assertEquals(1, cp.count());
        Assertions.assertEquals(0, cp.toByteArray().length);
    }

    @Test
    void utf8IsDeduplicated() {
        ConstantPool cp = new ConstantPool();
        int first = cp.utf8("Code");
        int second = cp.utf8("Code");
        Assertions.assertEquals(first, second);
        Assertions.assertEquals(2, cp.count());
        // tag=1，u2 长度=4，"Code"
        byte[] bytes = cp.toByteArray();
        Assertions.assertEquals(7, bytes.length);
        Assertions.assertEquals(1, bytes[0]);
        Assertions.assertEquals(0, bytes[1]);
        Assertions.assertEquals(4, bytes[2]);
        Assertions.assertEquals((byte) 'C', bytes[3]);
    }

    @Test
    void distinctUtf8GetDistinctIndices() {
        ConstantPool cp = new ConstantPool();
        int a = cp.utf8("foo");
        int b = cp.utf8("bar");
        Assertions.assertTrue(a != b, "distinct strings need distinct indices");
        Assertions.assertEquals(3, cp.count());
    }

    @Test
    void classRefSharesUtf8AndProducesClassEntry() {
        ConstantPool cp = new ConstantPool();
        int utf = cp.utf8("java/lang/Object");
        int cls = cp.classRef("java/lang/Object");
        Assertions.assertTrue(cls != utf, "class entry must be a distinct slot from its name utf8");
        Assertions.assertEquals(3, cp.count());
        // utf8 "java/lang/Object"（16 字符）= 3 + 16 = 19 字节，class 项 = 3 字节
        Assertions.assertEquals(22, cp.toByteArray().length);
    }

    @Test
    void methodRefChainsEntries() {
        ConstantPool cp = new ConstantPool();
        int ref = cp.methodRef("java/lang/Object", "toString", "()Ljava/lang/String;");
        Assertions.assertTrue(ref > 0);
        // Object utf8(19) + NameAndType("toString","()Ljava/lang/String;") 使用 2 个 utf8 + 5 = ...
        // 仅确保其可解析且重复添加时去重
        int again = cp.methodRef("java/lang/Object", "toString", "()Ljava/lang/String;");
        Assertions.assertEquals(ref, again);
    }
}
