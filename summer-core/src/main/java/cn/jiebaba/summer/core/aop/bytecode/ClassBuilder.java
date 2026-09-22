package cn.jiebaba.summer.core.aop.bytecode;

import cn.jiebaba.summer.core.aop.SummerProxy;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 为给定目标类的子类代理组装完整的 JVM class 文件。生成的类继承目标类、实现
 * {@link SummerProxy}，并为每个可覆写方法生成一个覆写（委托给
 * {@code SubclassProxyFactory.intercept}）外加一个 {@code $$summer$super$<name>}
 * bridge（{@code invokespecial super.<name>}）。
 */
public final class ClassBuilder {

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_SYNTHETIC = 0x1000;

    private ClassBuilder() {}

    /** 构建类字节码并在专属 ClassLoader 中定义代理类。 */
    public static Class<?> defineProxyClass(Class<?> targetClass, List<Method> methods,
                                            Constructor<?> ctor, String factoryInternal) {
        String proxyName = targetClass.getName() + "$$SummerProxy";
        byte[] bytes = build(proxyName.replace('.', '/'), targetClass, methods, ctor, factoryInternal);
        ProxyClassLoader cl = new ProxyClassLoader(targetClass.getClassLoader());
        return cl.define(proxyName, bytes);
    }

    /**
     * 构建代理类的字节码并在专属 ClassLoader 中定义该类。
     */
    static byte[] build(String proxyInternalName, Class<?> targetClass,
                        List<Method> methods, Constructor<?> ctor, String factoryInternal) {
        ConstantPool cp = new ConstantPool();
        String targetInternal = Descriptor.internalName(targetClass);

        int thisClass = cp.classRef(proxyInternalName);
        int superClass = cp.classRef(targetInternal);
        int markerInterface = cp.classRef(Descriptor.internalName(SummerProxy.class));

        List<byte[]> methodInfos = new ArrayList<>();
        for (int i = 0; i < methods.size(); i++) {
            Method m = methods.get(i);
            methodInfos.add(MethodBuilder.overrideMethod(cp, factoryInternal, m, i));
            methodInfos.add(MethodBuilder.bridgeMethod(cp, m, targetInternal));
        }
        String ctorDesc = Descriptor.of(ctor);
        methodInfos.add(MethodBuilder.constructor(cp, ctorDesc, ctor.getParameterTypes(), targetInternal));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xCA); out.write(0xFE); out.write(0xBA); out.write(0xBE); // 魔数
        Bytecode.u2(out, 0);   // minor_version
        Bytecode.u2(out, 69);  // major_version（Java 25）
        Bytecode.u2(out, cp.count());
        byte[] pool = cp.toByteArray();
        out.write(pool, 0, pool.length);
        Bytecode.u2(out, ACC_PUBLIC | ACC_SYNTHETIC); // access_flags
        Bytecode.u2(out, thisClass);
        Bytecode.u2(out, superClass);
        Bytecode.u2(out, 1);   // interfaces_count
        Bytecode.u2(out, markerInterface);
        Bytecode.u2(out, 0);   // fields_count
        Bytecode.u2(out, methodInfos.size()); // methods_count
        for (byte[] mi : methodInfos) out.write(mi, 0, mi.length);
        Bytecode.u2(out, 0);   // attributes_count
        return out.toByteArray();
    }
}
