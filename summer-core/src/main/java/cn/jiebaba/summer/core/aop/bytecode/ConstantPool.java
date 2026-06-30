package cn.jiebaba.summer.core.aop.bytecode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a JVM constant pool with on-demand deduplication. Indices are 1-based
 * (slot 0 is reserved). Only the entry kinds used by the proxy generator are
 * supported (no long/double).
 */
public final class ConstantPool {

    private final List<byte[]> entries = new ArrayList<>();
    private final Map<String, Integer> utf8Index = new HashMap<>();
    private final Map<String, Integer> classIndex = new HashMap<>();
    private final Map<String, Integer> nameAndTypeIndex = new HashMap<>();
    private final Map<String, Integer> methodRefIndex = new HashMap<>();
    private final Map<String, Integer> interfaceMethodRefIndex = new HashMap<>();
    private final Map<String, Integer> fieldRefIndex = new HashMap<>();
    private final Map<Integer, Integer> integerIndex = new HashMap<>();

    private int add(byte[] entry) {
        entries.add(entry);
        return entries.size(); // 1-based
    }

    public int utf8(String s) {
        return utf8Index.computeIfAbsent(s, k -> {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            byte[] e = new byte[3 + b.length];
            e[0] = 1; // CONSTANT_Utf8
            e[1] = (byte) (b.length >>> 8);
            e[2] = (byte) b.length;
            System.arraycopy(b, 0, e, 3, b.length);
            return add(e);
        });
    }

    public int classRef(String internalName) {
        return classIndex.computeIfAbsent(internalName, k -> {
            int ni = utf8(k);
            return add(new byte[]{7, hi(ni), lo(ni)}); // CONSTANT_Class
        });
    }

    public int nameAndType(String name, String descriptor) {
        return nameAndTypeIndex.computeIfAbsent(name + ':' + descriptor, k -> {
            int ni = utf8(name);
            int di = utf8(descriptor);
            return add(new byte[]{12, hi(ni), lo(ni), hi(di), lo(di)}); // CONSTANT_NameAndType
        });
    }

    public int methodRef(String owner, String name, String descriptor) {
        return methodRefIndex.computeIfAbsent(owner + '#' + name + descriptor, k -> {
            int ci = classRef(owner);
            int nti = nameAndType(name, descriptor);
            return add(new byte[]{10, hi(ci), lo(ci), hi(nti), lo(nti)}); // CONSTANT_Methodref
        });
    }

    public int interfaceMethodRef(String owner, String name, String descriptor) {
        return interfaceMethodRefIndex.computeIfAbsent(owner + '#' + name + descriptor, k -> {
            int ci = classRef(owner);
            int nti = nameAndType(name, descriptor);
            return add(new byte[]{11, hi(ci), lo(ci), hi(nti), lo(nti)}); // CONSTANT_InterfaceMethodref
        });
    }

    public int fieldRef(String owner, String name, String descriptor) {
        return fieldRefIndex.computeIfAbsent(owner + '#' + name + descriptor, k -> {
            int ci = classRef(owner);
            int nti = nameAndType(name, descriptor);
            return add(new byte[]{9, hi(ci), lo(ci), hi(nti), lo(nti)}); // CONSTANT_Fieldref
        });
    }

    public int integer(int value) {
        return integerIndex.computeIfAbsent(value, k ->
                add(new byte[]{3, (byte) (value >>> 24), (byte) (value >>> 16),
                        (byte) (value >>> 8), (byte) value})); // CONSTANT_Integer
    }

    /** constant_pool_count: number of entries + 1 (slot 0 is unused). */
    public int count() {
        return entries.size() + 1;
    }

    /** The concatenated bytes of every constant pool entry (without the count prefix). */
    public byte[] toByteArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] e : entries) out.write(e, 0, e.length);
        return out.toByteArray();
    }

    private static byte hi(int v) { return (byte) (v >>> 8); }
    private static byte lo(int v) { return (byte) v; }
}