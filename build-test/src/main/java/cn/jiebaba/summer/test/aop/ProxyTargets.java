package cn.jiebaba.summer.test.aop;

/** Target fixtures for subclass-proxy tests. Public so the generated proxy
 *  (defined in a child classloader) can legally extend them. */
public class ProxyTargets {

    public static class Simple {
        public String greet(String name) { return "hello " + name; }
    }

    public static class SelfCall {
        public String a() { return b() + "-a"; }
        public String b() { return "b"; }
    }

    public static class FinalMethod {
        public String normal() { return "normal"; }
        public final String fin() { return "final"; }
    }

    public static class ReturnTypes {
        public int addInt(int a, int b) { return a + b; }
        public long squareLong(long v) { return v * v; }
        public boolean neg(boolean v) { return !v; }
        public String[] dup(String s) { return new String[]{s, s}; }
        public void noop() {}
    }

    public static final class CannotSubclass {
        public String hi() { return "hi"; }
    }
}