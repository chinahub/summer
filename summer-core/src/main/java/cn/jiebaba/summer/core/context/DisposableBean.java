package cn.jiebaba.summer.core.context;

public interface DisposableBean {
    void destroy() throws Exception;
}
