package cn.jiebaba.summer.core.aop.bytecode;

import java.io.ByteArrayOutputStream;

/** Small helpers for writing big-endian u2/u4 values into a byte stream. */
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