package cn.jiebaba.summer.core.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class BeanDefinition {
    public static final String SCOPE_SINGLETON = "singleton";
    public static final String SCOPE_PROTOTYPE = "prototype";

    private String name;
    private Class<?> beanClass;
    private String scope = SCOPE_SINGLETON;
    private boolean primary = false;
    private boolean lazyInit = false;
    private String factoryBeanName;
    private Method factoryMethod;
    private Constructor<?> constructor;
    private String initMethodName;
    private String destroyMethodName;
    private boolean synthetic = false;
    private java.util.function.Supplier<Object> instanceSupplier;

    public BeanDefinition() {}

    public BeanDefinition(String name, Class<?> beanClass) {
        this.name = name;
        this.beanClass = beanClass;
    }

    public boolean isSingleton() { return SCOPE_SINGLETON.equalsIgnoreCase(scope); }
    public boolean isPrototype() { return SCOPE_PROTOTYPE.equalsIgnoreCase(scope); }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Class<?> getBeanClass() { return beanClass; }
    public void setBeanClass(Class<?> beanClass) { this.beanClass = beanClass; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public boolean isLazyInit() { return lazyInit; }
    public void setLazyInit(boolean lazyInit) { this.lazyInit = lazyInit; }
    public String getFactoryBeanName() { return factoryBeanName; }
    public void setFactoryBeanName(String factoryBeanName) { this.factoryBeanName = factoryBeanName; }
    public Method getFactoryMethod() { return factoryMethod; }
    public void setFactoryMethod(Method factoryMethod) { this.factoryMethod = factoryMethod; }
    public Constructor<?> getConstructor() { return constructor; }
    public void setConstructor(Constructor<?> constructor) { this.constructor = constructor; }
    public String getInitMethodName() { return initMethodName; }
    public void setInitMethodName(String initMethodName) { this.initMethodName = initMethodName; }
    public String getDestroyMethodName() { return destroyMethodName; }
    public void setDestroyMethodName(String destroyMethodName) { this.destroyMethodName = destroyMethodName; }
    public boolean isSynthetic() { return synthetic; }
    public void setSynthetic(boolean synthetic) { this.synthetic = synthetic; }
    public java.util.function.Supplier<Object> getInstanceSupplier() { return instanceSupplier; }
    public void setInstanceSupplier(java.util.function.Supplier<Object> instanceSupplier) { this.instanceSupplier = instanceSupplier; }

    @Override
    public String toString() {
        return "BeanDefinition{name='" + name + "', class=" + beanClass + ", scope='" + scope + "'}";
    }
}
