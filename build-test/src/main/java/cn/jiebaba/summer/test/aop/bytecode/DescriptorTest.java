package cn.jiebaba.summer.test.aop.bytecode;

import cn.jiebaba.summer.core.aop.bytecode.Bytecode;
import cn.jiebaba.summer.core.aop.bytecode.Descriptor;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

import java.lang.reflect.Method;
import java.util.Map;

public class DescriptorTest {

    @Test
    void primitiveDescriptors() {
        Assert.assertEquals("V", Descriptor.of(void.class));
        Assert.assertEquals("I", Descriptor.of(int.class));
        Assert.assertEquals("J", Descriptor.of(long.class));
        Assert.assertEquals("F", Descriptor.of(float.class));
        Assert.assertEquals("D", Descriptor.of(double.class));
        Assert.assertEquals("Z", Descriptor.of(boolean.class));
        Assert.assertEquals("B", Descriptor.of(byte.class));
        Assert.assertEquals("C", Descriptor.of(char.class));
        Assert.assertEquals("S", Descriptor.of(short.class));
    }

    @Test
    void referenceAndArrayDescriptors() {
        Assert.assertEquals("Ljava/lang/String;", Descriptor.of(String.class));
        Assert.assertEquals("Ljava/util/Map;", Descriptor.of(Map.class));
        Assert.assertEquals("[I", Descriptor.of(int[].class));
        Assert.assertEquals("[Ljava/lang/String;", Descriptor.of(String[].class));
        Assert.assertEquals("[[I", Descriptor.of(int[][].class));
    }

    @Test
    void methodDescriptors() throws Exception {
        Method length = String.class.getMethod("length");
        Assert.assertEquals("()I", Descriptor.of(length));

        Method substring = String.class.getMethod("substring", int.class);
        Assert.assertEquals("(I)Ljava/lang/String;", Descriptor.of(substring));

        Method equals = Object.class.getMethod("equals", Object.class);
        Assert.assertEquals("(Ljava/lang/Object;)Z", Descriptor.of(equals));

        Method toCharArray = String.class.getMethod("toCharArray");
        Assert.assertEquals("()[C", Descriptor.of(toCharArray));
    }

    @Test
    void slotsForTypes() {
        Assert.assertEquals(2, Descriptor.slots(long.class));
        Assert.assertEquals(2, Descriptor.slots(double.class));
        Assert.assertEquals(1, Descriptor.slots(int.class));
        Assert.assertEquals(1, Descriptor.slots(Object.class));
        Assert.assertEquals(1, Descriptor.slots(boolean.class));
    }

    @Test
    void internalNameForClassAndArray() {
        Assert.assertEquals("java/lang/String", Descriptor.internalName(String.class));
        Assert.assertEquals("[I", Descriptor.internalName(int[].class));
        Assert.assertEquals("[Ljava/lang/String;", Descriptor.internalName(String[].class));
    }
}
