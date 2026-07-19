package cn.jiebaba.summer.test.aop.bytecode;

import cn.jiebaba.summer.core.aop.bytecode.Bytecode;
import cn.jiebaba.summer.core.aop.bytecode.Descriptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

public class DescriptorTest {

    @Test
    void primitiveDescriptors() {
        Assertions.assertEquals("V", Descriptor.of(void.class));
        Assertions.assertEquals("I", Descriptor.of(int.class));
        Assertions.assertEquals("J", Descriptor.of(long.class));
        Assertions.assertEquals("F", Descriptor.of(float.class));
        Assertions.assertEquals("D", Descriptor.of(double.class));
        Assertions.assertEquals("Z", Descriptor.of(boolean.class));
        Assertions.assertEquals("B", Descriptor.of(byte.class));
        Assertions.assertEquals("C", Descriptor.of(char.class));
        Assertions.assertEquals("S", Descriptor.of(short.class));
    }

    @Test
    void referenceAndArrayDescriptors() {
        Assertions.assertEquals("Ljava/lang/String;", Descriptor.of(String.class));
        Assertions.assertEquals("Ljava/util/Map;", Descriptor.of(Map.class));
        Assertions.assertEquals("[I", Descriptor.of(int[].class));
        Assertions.assertEquals("[Ljava/lang/String;", Descriptor.of(String[].class));
        Assertions.assertEquals("[[I", Descriptor.of(int[][].class));
    }

    @Test
    void methodDescriptors() throws Exception {
        Method length = String.class.getMethod("length");
        Assertions.assertEquals("()I", Descriptor.of(length));

        Method substring = String.class.getMethod("substring", int.class);
        Assertions.assertEquals("(I)Ljava/lang/String;", Descriptor.of(substring));

        Method equals = Object.class.getMethod("equals", Object.class);
        Assertions.assertEquals("(Ljava/lang/Object;)Z", Descriptor.of(equals));

        Method toCharArray = String.class.getMethod("toCharArray");
        Assertions.assertEquals("()[C", Descriptor.of(toCharArray));
    }

    @Test
    void slotsForTypes() {
        Assertions.assertEquals(2, Descriptor.slots(long.class));
        Assertions.assertEquals(2, Descriptor.slots(double.class));
        Assertions.assertEquals(1, Descriptor.slots(int.class));
        Assertions.assertEquals(1, Descriptor.slots(Object.class));
        Assertions.assertEquals(1, Descriptor.slots(boolean.class));
    }

    @Test
    void internalNameForClassAndArray() {
        Assertions.assertEquals("java/lang/String", Descriptor.internalName(String.class));
        Assertions.assertEquals("[I", Descriptor.internalName(int[].class));
        Assertions.assertEquals("[Ljava/lang/String;", Descriptor.internalName(String[].class));
    }
}
