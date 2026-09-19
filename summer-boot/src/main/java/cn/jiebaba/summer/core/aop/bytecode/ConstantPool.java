package cn.jiebaba.summer.core.aop.bytecode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建带按需去重的 JVM 常量池。索引从 1 开始（槽位 0 保留）。仅支持代理生成器用到的
 * 条目类型（不含 long/double）。
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
        return entries.size(); // 从 1 开始
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

    /** constant_pool_count：条目数 + 1（槽 0 未使用）。 */
    public int count() {
        return entries.size() + 1;
    }

    /** 所有常量池条目的拼接字节（不含计数前缀）。 */
    public byte[] toByteArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] e : entries) out.write(e, 0, e.length);
        return out.toByteArray();
    }

    private static byte hi(int v) { return (byte) (v >>> 8); }
    private static byte lo(int v) { return (byte) v; }
}
