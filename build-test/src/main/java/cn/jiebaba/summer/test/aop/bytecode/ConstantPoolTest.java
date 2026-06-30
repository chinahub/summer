package cn.jiebaba.summer.test.aop.bytecode;

import cn.jiebaba.summer.core.aop.bytecode.Bytecode;
import cn.jiebaba.summer.core.aop.bytecode.ConstantPool;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

public class ConstantPoolTest {

    @Test
    void emptyPoolHasCountOne() {
        ConstantPool cp = new ConstantPool();
        Assert.assertEquals(1, cp.count());
        Assert.assertEquals(0, cp.toByteArray().length);
    }

    @Test
    void utf8IsDeduplicated() {
        ConstantPool cp = new ConstantPool();
        int first = cp.utf8("Code");
        int second = cp.utf8("Code");
        Assert.assertEquals(first, second);
        Assert.assertEquals(2, cp.count());
        // tag=1, u2 length=4, "Code"
        byte[] bytes = cp.toByteArray();
        Assert.assertEquals(7, bytes.length);
        Assert.assertEquals(1, bytes[0]);
        Assert.assertEquals(0, bytes[1]);
        Assert.assertEquals(4, bytes[2]);
        Assert.assertEquals((byte) 'C', bytes[3]);
    }

    @Test
    void distinctUtf8GetDistinctIndices() {
        ConstantPool cp = new ConstantPool();
        int a = cp.utf8("foo");
        int b = cp.utf8("bar");
        Assert.assertTrue(a != b, "distinct strings need distinct indices");
        Assert.assertEquals(3, cp.count());
    }

    @Test
    void classRefSharesUtf8AndProducesClassEntry() {
        ConstantPool cp = new ConstantPool();
        int utf = cp.utf8("java/lang/Object");
        int cls = cp.classRef("java/lang/Object");
        Assert.assertTrue(cls != utf, "class entry must be a distinct slot from its name utf8");
        Assert.assertEquals(3, cp.count());
        // utf8 "java/lang/Object" (16 chars) = 3 + 16 = 19 bytes, class entry = 3 bytes
        Assert.assertEquals(22, cp.toByteArray().length);
    }

    @Test
    void methodRefChainsEntries() {
        ConstantPool cp = new ConstantPool();
        int ref = cp.methodRef("java/lang/Object", "toString", "()Ljava/lang/String;");
        Assert.assertTrue(ref > 0);
        // Object utf8(19) + NameAndType("toString","()Ljava/lang/String;") uses 2 utf8 + 5 = ...
        // just ensure it is resolvable and dedups on repeat
        int again = cp.methodRef("java/lang/Object", "toString", "()Ljava/lang/String;");
        Assert.assertEquals(ref, again);
    }
}
