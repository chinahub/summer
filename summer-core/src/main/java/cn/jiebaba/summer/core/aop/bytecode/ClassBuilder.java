package cn.jiebaba.summer.core.aop.bytecode;

import cn.jiebaba.summer.core.aop.SummerProxy;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a complete JVM class file for a subclass proxy of the given target
 * class. The generated class extends the target, implements {@link SummerProxy},
 * and for every overridable method emits an override (delegating to
 * {@code SubclassProxyFactory.intercept}) plus a {@code $$summer$super$<name>}
 * bridge ({@code invokespecial super.<name>}).
 */
public final class ClassBuilder {

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_SYNTHETIC = 0x1000;

    private ClassBuilder() {}

    /** Builds the class bytes and defines the proxy class in a dedicated classloader. */
    public static Class<?> defineProxyClass(Class<?> targetClass, List<Method> methods,
                                            Constructor<?> ctor, String factoryInternal) {
        String proxyName = targetClass.getName() + "$$SummerProxy";
        byte[] bytes = build(proxyName.replace('.', '/'), targetClass, methods, ctor, factoryInternal);
        ProxyClassLoader cl = new ProxyClassLoader(targetClass.getClassLoader());
        return cl.define(proxyName, bytes);
    }

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
        out.write(0xCA); out.write(0xFE); out.write(0xBA); out.write(0xBE); // magic
        Bytecode.u2(out, 0);   // minor_version
        Bytecode.u2(out, 69);  // major_version (Java 25)
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