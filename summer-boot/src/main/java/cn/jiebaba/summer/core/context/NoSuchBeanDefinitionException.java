package cn.jiebaba.summer.core.context;

public class NoSuchBeanDefinitionException extends BeansException {
    public NoSuchBeanDefinitionException(String message) { super(message); }
    public NoSuchBeanDefinitionException(Class<?> type) { super("No bean of type [" + type.getName() + "] defined"); }
    public NoSuchBeanDefinitionException(String name, Class<?> type) {
        super("No bean named [" + name + "] of type [" + (type == null ? "?" : type.getName()) + "] defined");
    }
}
