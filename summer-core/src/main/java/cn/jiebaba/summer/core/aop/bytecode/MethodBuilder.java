package cn.jiebaba.summer.core.aop.bytecode;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * Emits the {@code method_info} byte arrays for the three kinds of methods on a
 * generated subclass proxy:
 * <ul>
 *   <li><b>override</b> — delegates to {@code SubclassProxyFactory.intercept}</li>
 *   <li><b>bridge</b> — {@code $$summer$super$<name>} calling {@code invokespecial super.<name>}</li>
 *   <li><b>constructor</b> — forwards to {@code super.<init>}</li>
 * </ul>
 * All method bodies are strictly linear (no branches / no try-catch) so that no
 * {@code StackMapTable} attribute is required.
 */
final class MethodBuilder {

    static final int ACC_PUBLIC = 0x0001;
    static final int ACC_PRIVATE = 0x0002;
    static final int ACC_PROTECTED = 0x0004;
    static final int ACC_SYNTHETIC = 0x1000;

    private static final Map<Class<?>, String> WRAPPER = Map.of(
            int.class, "java/lang/Integer",
            long.class, "java/lang/Long",
            float.class, "java/lang/Float",
            double.class, "java/lang/Double",
            boolean.class, "java/lang/Boolean",
            byte.class, "java/lang/Byte",
            char.class, "java/lang/Character",
            short.class, "java/lang/Short");

    private static final Map<Class<?>, String> UNBOX = Map.of(
            int.class, "intValue",
            long.class, "longValue",
            float.class, "floatValue",
            double.class, "doubleValue",
            boolean.class, "booleanValue",
            byte.class, "byteValue",
            char.class, "charValue",
            short.class, "shortValue");

    private static final Map<Class<?>, String> PRIM = Map.of(
            int.class, "I", long.class, "J", float.class, "F", double.class, "D",
            boolean.class, "Z", byte.class, "B", char.class, "C", short.class, "S");

    private MethodBuilder() {}

    /** Generates an override that calls {@code factoryInternal.intercept(this, index, args)}. */
    static byte[] overrideMethod(ConstantPool cp, String factoryInternal, Method m, int methodIndex) {
        String desc = Descriptor.of(m);
        Class<?>[] params = m.getParameterTypes();
        Class<?> ret = m.getReturnType();

        ByteArrayOutputStream c = new ByteArrayOutputStream();
        emitLoadRef(c, 0);                       // this (proxy)
        emitIntConst(c, cp, methodIndex);        // method index
        emitNewArgsArray(c, cp, params);         // Object[] args
        int imr = cp.methodRef(factoryInternal, "intercept",
                "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;");
        c.write(184); Bytecode.u2(c, imr);       // invokestatic
        emitReturn(c, cp, ret);

        int maxStack = Math.max(8, 1 + totalSlots(params) + 4);
        int maxLocals = 1 + totalSlots(params);
        int access = ACC_SYNTHETIC | (Modifier.isPublic(m.getModifiers()) ? ACC_PUBLIC : ACC_PROTECTED);
        return methodInfo(cp, access, m.getName(), desc, c.toByteArray(), maxStack, maxLocals);
    }

    /** Generates {@code $$summer$super$<name>} that calls {@code invokespecial super.<name>}. */
    static byte[] bridgeMethod(ConstantPool cp, Method m, String superOwner) {
        String name = "$$summer$super$" + m.getName();
        String desc = Descriptor.of(m);
        Class<?>[] params = m.getParameterTypes();
        Class<?> ret = m.getReturnType();

        ByteArrayOutputStream c = new ByteArrayOutputStream();
        emitLoadRef(c, 0);                       // this
        int local = 1;
        for (Class<?> pt : params) {
            emitLoad(c, pt, local);
            local += Descriptor.slots(pt);
        }
        int mr = cp.methodRef(superOwner, m.getName(), desc);
        c.write(183); Bytecode.u2(c, mr);        // invokespecial super.<name>
        c.write(returnOpcode(ret));

        int retSlots = (ret == void.class) ? 0 : Descriptor.slots(ret);
        int maxStack = Math.max(1 + totalSlots(params), retSlots);
        int maxLocals = 1 + totalSlots(params);
        return methodInfo(cp, ACC_PRIVATE | ACC_SYNTHETIC, name, desc, c.toByteArray(), maxStack, maxLocals);
    }

    /** Generates a constructor that forwards to {@code super.<init>}. */
    static byte[] constructor(ConstantPool cp, String desc, Class<?>[] params, String superOwner) {
        ByteArrayOutputStream c = new ByteArrayOutputStream();
        emitLoadRef(c, 0);                       // this
        int local = 1;
        for (Class<?> pt : params) {
            emitLoad(c, pt, local);
            local += Descriptor.slots(pt);
        }
        int mr = cp.methodRef(superOwner, "<init>", desc);
        c.write(183); Bytecode.u2(c, mr);        // invokespecial super.<init>
        c.write(177);                            // return

        int maxStack = 1 + totalSlots(params);
        int maxLocals = 1 + totalSlots(params);
        return methodInfo(cp, ACC_PUBLIC, "<init>", desc, c.toByteArray(), maxStack, maxLocals);
    }

    // ---- bytecode emission helpers ----

    private static void emitNewArgsArray(ByteArrayOutputStream c, ConstantPool cp, Class<?>[] params) {
        int objClass = cp.classRef("java/lang/Object");
        emitIntConst(c, cp, params.length);
        c.write(189); Bytecode.u2(c, objClass);  // anewarray Object
        int local = 1;
        for (int i = 0; i < params.length; i++) {
            c.write(89);                          // dup
            emitIntConst(c, cp, i);
            Class<?> pt = params[i];
            emitLoad(c, pt, local);
            emitBox(c, cp, pt);
            c.write(83);                          // aastore
            local += Descriptor.slots(pt);
        }
    }

    private static void emitBox(ByteArrayOutputStream c, ConstantPool cp, Class<?> t) {
        if (!t.isPrimitive()) return;
        String w = WRAPPER.get(t);
        int mr = cp.methodRef(w, "valueOf", "(" + PRIM.get(t) + ")L" + w + ";");
        c.write(184); Bytecode.u2(c, mr);        // invokestatic valueOf
    }

    private static void emitReturn(ByteArrayOutputStream c, ConstantPool cp, Class<?> rt) {
        if (rt == void.class) {
            c.write(87);                          // pop
            c.write(177);                         // return
            return;
        }
        if (rt.isPrimitive()) {
            String w = WRAPPER.get(rt);
            int cc = cp.classRef(w);
            c.write(192); Bytecode.u2(c, cc);    // checkcast wrapper
            int mr = cp.methodRef(w, UNBOX.get(rt), "()" + PRIM.get(rt));
            c.write(182); Bytecode.u2(c, mr);    // invokevirtual xxxValue()
            c.write(returnOpcode(rt));
        } else {
            int cc = cp.classRef(Descriptor.internalName(rt));
            c.write(192); Bytecode.u2(c, cc);    // checkcast
            c.write(176);                         // areturn
        }
    }

    private static int returnOpcode(Class<?> rt) {
        if (rt == void.class) return 177;
        if (rt == long.class) return 173;
        if (rt == float.class) return 174;
        if (rt == double.class) return 175;
        if (rt.isPrimitive()) return 172;        // int-ish
        return 176;                              // ref/array
    }

    private static void emitLoadRef(ByteArrayOutputStream c, int index) {
        c.write(25); c.write(index);             // aload
    }

    private static void emitLoad(ByteArrayOutputStream c, Class<?> t, int index) {
        int op;
        if (!t.isPrimitive()) op = 25;           // aload
        else if (t == long.class) op = 22;       // lload
        else if (t == float.class) op = 23;      // fload
        else if (t == double.class) op = 24;     // dload
        else op = 21;                            // iload
        c.write(op);
        c.write(index);
    }

    private static void emitIntConst(ByteArrayOutputStream c, ConstantPool cp, int v) {
        if (v == -1) { c.write(2); return; }              // iconst_m1
        if (v >= 0 && v <= 5) { c.write(3 + v); return; } // iconst_0..5
        if (v >= -128 && v <= 127) { c.write(16); c.write(v); return; } // bipush
        if (v >= -32768 && v <= 32767) { c.write(17); c.write(v >>> 8); c.write(v); return; } // sipush
        int idx = cp.integer(v);
        c.write(19); Bytecode.u2(c, idx);        // ldc_w
    }

    private static int totalSlots(Class<?>[] types) {
        int s = 0;
        for (Class<?> t : types) s += Descriptor.slots(t);
        return s;
    }

    private static byte[] methodInfo(ConstantPool cp, int access, String name, String desc,
                                     byte[] code, int maxStack, int maxLocals) {
        int nameIdx = cp.utf8(name);
        int descIdx = cp.utf8(desc);
        int codeAttr = cp.utf8("Code");
        ByteArrayOutputStream mi = new ByteArrayOutputStream();
        Bytecode.u2(mi, access);
        Bytecode.u2(mi, nameIdx);
        Bytecode.u2(mi, descIdx);
        Bytecode.u2(mi, 1);                      // attributes_count (Code only)
        Bytecode.u2(mi, codeAttr);
        Bytecode.u4(mi, 2 + 2 + 4 + code.length + 2 + 2); // attribute_length
        Bytecode.u2(mi, maxStack);
        Bytecode.u2(mi, maxLocals);
        Bytecode.u4(mi, code.length);
        mi.write(code, 0, code.length);
        Bytecode.u2(mi, 0);                      // exception_table_length
        Bytecode.u2(mi, 0);                      // attributes_count (no StackMapTable)
        return mi.toByteArray();
    }
}