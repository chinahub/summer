package cn.jiebaba.summer.core.aop.bytecode;

import java.io.ByteArrayOutputStream;

/** 向字节流写入大端序 u2/u4 值的小工具。 */
public final class Bytecode {
    private Bytecode() {}

    public static void u2(ByteArrayOutputStream o, int v) {
        o.write(v >>> 8);
        o.write(v);
    }

    public static void u4(ByteArrayOutputStream o, int v) {
        o.write(v >>> 24);
        o.write(v >>> 16);
        o.write(v >>> 8);
        o.write(v);
    }
}
