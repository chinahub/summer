package cn.jiebaba.summer.core.context;

import cn.jiebaba.summer.core.annotation.*;
import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.annotation.Lazy;
import cn.jiebaba.summer.core.annotation.PostConstruct;
import cn.jiebaba.summer.core.annotation.PreDestroy;
import cn.jiebaba.summer.core.annotation.Qualifier;
import cn.jiebaba.summer.core.aop.AdvisedProxyFactory;
import cn.jiebaba.summer.core.aop.Aspect;
import cn.jiebaba.summer.core.aop.AspectRegistry;
import cn.jiebaba.summer.core.aop.MethodInterceptor;
import cn.jiebaba.summer.core.aop.ProxyAdvisor;
import cn.jiebaba.summer.core.aop.SubclassProxyFactory;
import cn.jiebaba.summer.core.aop.SummerProxy;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.core.scanner.AnnotationUtils;
import cn.jiebaba.summer.core.scanner.ClassPathScanner;
import cn.jiebaba.summer.core.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DefaultApplicationContext implements ApplicationContext {

    private static final Logger LOG = Logger.getLogger(DefaultApplicationContext.class.getName());

    private final Map<String, BeanDefinition> beanDefinitions = new LinkedHashMap<>();
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>();
    private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>();
    private final Set<String> inCreation = ConcurrentHashMap.newKeySet();
    private final List<String> destructionOrder = Collections.synchronizedList(new ArrayList<>());
    private final AspectRegistry aspectRegistry = new AspectRegistry();
    private final List<ProxyAdvisor> advisors = new ArrayList<>();
    private final Map<String, Object> targetObjects = new ConcurrentHashMap<>();

    private final Environment environment;
    private final ClassLoader classLoader;
    private final Set<String> basePackages;
    private volatile boolean running = false;

    public DefaultApplicationContext(ClassLoader classLoader, Environment environment, Set<String> basePackages) {
        this.classLoader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        this.environment = environment != null ? environment : new Environment();
        this.basePackages = basePackages != null ? basePackages : Set.of();
    }

    public void refresh() {
        LOG.info("summer: refreshing application context, base packages=" + basePackages);
        scanAndRegister();
        processBeanMethods();
        preInstantiateSingletons();
        running = true;
        LOG.info("summer: context ready with " + singletonObjects.size() + " singletons");
    }

    private void scanAndRegister() {
        Set<Class<?>> candidates = ClassPathScanner.scan(basePackages, classLoader);
        for (Class<?> clazz : candidates) {
            if (clazz.isAnnotation() || clazz.isInterface() || clazz.isEnum() || clazz.isRecord()) continue;
            if (clazz.isAnonymousClass() || clazz.isLocalClass()) continue;
            if (!AnnotationUtils.hasAnnotation(clazz, Component.class)) continue;
            registerComponent(clazz);
        }
    }

    private void registerComponent(Class<?> clazz) {
        BeanDefinition def = new BeanDefinition(decapitalize(clazz.getSimpleName()), clazz);
        String name = readStereotypeName(clazz);
        if (name != null && !name.isEmpty()) def.setName(name);
        Scope scope = AnnotationUtils.findAnnotation(clazz, Scope.class);
        if (scope != null) def.setScope(scope.value());
        if (AnnotationUtils.hasAnnotation(clazz, Primary.class)) def.setPrimary(true);
        if (AnnotationUtils.hasAnnotation(clazz, Lazy.class)) def.setLazyInit(true);
        def.setConstructor(chooseConstructor(clazz));
        registerBeanDefinition(def.getName(), def);
    }

    /**
     * 处理 Bean 上的 @Bean 等工厂方法，注册其产生的 Bean 定义。
     */
    private void processBeanMethods() {
        for (BeanDefinition def : new ArrayList<>(beanDefinitions.values())) {
            Class<?> configClass = def.getBeanClass();
            if (def.getFactoryMethod() != null) continue;
            if (!AnnotationUtils.hasAnnotation(configClass, Configuration.class)) continue;
            for (Method method : configClass.getDeclaredMethods()) {
                Bean bean = method.getAnnotation(Bean.class);
                if (bean == null) continue;
                BeanDefinition bd = new BeanDefinition();
                bd.setBeanClass(method.getReturnType());
                String n = firstNonEmpty(bean.value(), bean.name());
                bd.setName((n != null && !n.isEmpty()) ? n : method.getName());
                bd.setScope(method.isAnnotationPresent(Scope.class) ? method.getAnnotation(Scope.class).value() : BeanDefinition.SCOPE_SINGLETON);
                bd.setPrimary(bean.primary() || method.isAnnotationPresent(Primary.class));
                bd.setLazyInit(method.isAnnotationPresent(Lazy.class));
                bd.setFactoryBeanName(def.getName());
                bd.setFactoryMethod(method);
                bd.setInitMethodName(firstNonEmpty(bean.initMethod(), null));
                bd.setDestroyMethodName(firstNonEmpty(bean.destroyMethod(), null));
                registerBeanDefinition(bd.getName(), bd);
            }
        }
    }

    private void preInstantiateSingletons() {
        List<String> aspects = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (String name : beanDefinitions.keySet()) {
            BeanDefinition def = beanDefinitions.get(name);
            if (def.isSynthetic() || !def.isSingleton() || def.isLazyInit()) continue;
            if (isAspectOrAdvisor(def)) aspects.add(name); else others.add(name);
        }
        for (String name : aspects) getBean(name);
        collectAopRegistries();
        for (String name : others) getBean(name);
    }

    private boolean isAspectOrAdvisor(BeanDefinition def) {
        Class<?> c = def.getBeanClass();
        if (c == null) return false;
        return AnnotationUtils.hasAnnotation(c, Aspect.class) || ProxyAdvisor.class.isAssignableFrom(c);
    }

    private void collectAopRegistries() {
        advisors.clear();
        for (Object bean : singletonObjects.values()) {
            if (bean instanceof ProxyAdvisor pa) advisors.add(pa);
            if (AnnotationUtils.hasAnnotation(bean.getClass(), Aspect.class)) aspectRegistry.registerAspect(bean);
        }
    }

    @Override
    public Object getBean(String name) {
        BeanDefinition def = beanDefinitions.get(name);
        if (def == null) throw new NoSuchBeanDefinitionException(name, null);
        return getBeanForDefinition(name, def);
    }

    private Object getBeanForDefinition(String name, BeanDefinition def) {
        if (def.isSingleton()) {
            Object existing = singletonObjects.get(name);
            if (existing != null) return existing;
            Object early = earlySingletonObjects.get(name);
            if (early != null) return early;
            return createSingleton(name, def);
        }
        return doCreateBean(name, def);
    }

    private Object createSingleton(String name, BeanDefinition def) {
        if (!inCreation.add(name)) {
            Object early = earlySingletonObjects.get(name);
            if (early != null) return early;
            throw new BeansException("Circular dependency detected for bean '" + name
                    + "' that cannot be resolved (constructor injection cycle).");
        }
        try {
            Object bean = doCreateBean(name, def);
            singletonObjects.put(name, bean);
            earlySingletonObjects.remove(name);
            destructionOrder.add(name);
            return bean;
        } finally {
            inCreation.remove(name);
        }
    }

    private Object doCreateBean(String name, BeanDefinition def) {
        Class<?> beanClass = def.getBeanClass();
        if (needsSubclassProxy(beanClass, def)) {
            Object proxy = createSubclassProxy(name, def, beanClass);
            if (def.isSingleton()) {
                earlySingletonObjects.put(name, proxy);
            }
            populateBean(proxy, def);
            initializeBean(name, proxy, def);
            return proxy;
        }
        Object target = instantiate(name, def);
        Object exposed = maybeWrapInJdkProxy(name, target);
        if (def.isSingleton()) {
            earlySingletonObjects.put(name, exposed);
        }
        populateBean(target, def);
        initializeBean(name, target, def);
        return exposed;
    }

    /** 无接口、非 final、构造器实例化且需要拦截的 Bean 通过子类代理增强。 */
    private boolean needsSubclassProxy(Class<?> beanClass, BeanDefinition def) {
        if (beanClass.getInterfaces().length > 0) return false;
        if (def.getFactoryMethod() != null || def.getInstanceSupplier() != null) return false;
        if (Modifier.isFinal(beanClass.getModifiers())) return false;
        if (!collectInterceptors(beanClass).isEmpty()) return true;
        return aspectRegistry.hasAdviceFor(beanClass);
    }

    private List<MethodInterceptor> collectInterceptors(Class<?> beanClass) {
        List<MethodInterceptor> interceptors = new ArrayList<>();
        for (ProxyAdvisor advisor : advisors) {
            if (advisor.advises(beanClass)) interceptors.addAll(advisor.interceptors());
        }
        return interceptors;
    }

    private Object createSubclassProxy(String name, BeanDefinition def, Class<?> beanClass) {
        List<MethodInterceptor> interceptors = collectInterceptors(beanClass);
        Constructor<?> ctor = def.getConstructor();
        if (ctor == null) ctor = chooseConstructor(beanClass);
        Object[] args = resolveExecutableArgs(ctor.getParameters(), ctor.getGenericParameterTypes());
        return SubclassProxyFactory.create(beanClass, ctor, args, interceptors, aspectRegistry.advices());
    }

    private Object maybeWrapInJdkProxy(String name, Object target) {
        Class<?> beanClass = target.getClass();
        List<MethodInterceptor> interceptors = collectInterceptors(beanClass);
        boolean aspectMatch = aspectRegistry.hasAdviceFor(beanClass);
        if (interceptors.isEmpty() && !aspectMatch) return target;
        if (beanClass.getInterfaces().length == 0) {
            throw new BeansException("Bean '" + name + "' (" + beanClass.getName()
                    + ") requires an AOP proxy (matched by @Transactional or @Aspect advice)"
                    + " but implements no interface and cannot be subclassed (it is final or"
                    + " created via a factory method). Either extract an interface, make the"
                    + " class non-final with a constructor, or remove the proxy-requiring annotation.");
        }
        Object proxy = AdvisedProxyFactory.createProxy(
                target, beanClass.getInterfaces(), interceptors, aspectRegistry.advices());
        targetObjects.put(name, target);
        return proxy;
    }

    /** 为注解扫描，子类代理通过超类暴露其用户类。 */
    private Class<?> effectiveType(Object bean) {
        return (bean instanceof SummerProxy) ? bean.getClass().getSuperclass() : bean.getClass();
    }

    /**
     * 实例化指定 Bean 定义：执行构造器注入并按需生成代理。
     */
    private Object instantiate(String name, BeanDefinition def) {
        if (def.getInstanceSupplier() != null) {
            return def.getInstanceSupplier().get();
        }
        try {
            if (def.getFactoryMethod() != null) {
                Object factoryBean = getBean(def.getFactoryBeanName());
                Method m = def.getFactoryMethod();
                ReflectionUtils.makeAccessible(m);
                Object[] args = resolveExecutableArgs(m.getParameters(), m.getGenericParameterTypes());
                return ReflectionUtils.invokeMethod(m, factoryBean, args);
            }
            Constructor<?> ctor = def.getConstructor();
            if (ctor == null) ctor = chooseConstructor(def.getBeanClass());
            ReflectionUtils.makeAccessible(ctor);
            Object[] args = resolveExecutableArgs(ctor.getParameters(), ctor.getGenericParameterTypes());
            return ctor.newInstance(args);
        } catch (BeansException e) {
            throw e;
        } catch (Throwable t) {
            Throwable cause = (t instanceof java.lang.reflect.InvocationTargetException ite) ? ite.getCause() : t;
            throw new BeansException("Failed to instantiate bean '" + name + "': " + cause.getMessage(), cause);
        }
    }

    private Object[] resolveExecutableArgs(Parameter[] params, Type[] genericTypes) {
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            Value value = p.getAnnotation(Value.class);
            if (value != null) {
                args[i] = resolveValue(value.value(), p.getType());
                continue;
            }
            Qualifier qualifier = p.getAnnotation(Qualifier.class);
            String q = qualifier != null ? qualifier.value() : null;
            args[i] = resolveDependency(p.getType(), genericTypes[i], q, p.getName(),
                    p.isAnnotationPresent(Autowired.class) ? p.getAnnotation(Autowired.class).required() : true);
        }
        return args;
    }

    /**
     * 对已实例化的 Bean 进行属性注入与依赖填充。
     */
    private void populateBean(Object bean, BeanDefinition def) {
        for (Field field : ReflectionUtils.collectFields(bean.getClass())) {
            if (field.isAnnotationPresent(Value.class)) {
                ReflectionUtils.makeAccessible(field);
                try {
                    field.set(bean, resolveValue(field.getAnnotation(Value.class).value(), field.getType()));
                } catch (IllegalAccessException e) {
                    throw new BeansException("Failed to set @Value field " + field, e);
                }
                continue;
            }
            if (field.isAnnotationPresent(Autowired.class)) {
                boolean required = field.getAnnotation(Autowired.class).required();
                Qualifier qualifier = field.getAnnotation(Qualifier.class);
                String q = qualifier != null ? qualifier.value() : null;
                Class<?> fieldType = resolveActualType(field.getGenericType(), bean.getClass());
                if (fieldType == null) fieldType = field.getType();
                Object value = resolveDependency(fieldType, field.getGenericType(), q, field.getName(), required);
                if (value == null) continue;
                ReflectionUtils.makeAccessible(field);
                try {
                    field.set(bean, value);
                } catch (IllegalAccessException e) {
                    throw new BeansException("Failed to @Autowired field " + field, e);
                }
            }
        }
        for (Method method : effectiveType(bean).getMethods()) {
            if (method.isAnnotationPresent(Autowired.class) && method.getParameterCount() > 0) {
                ReflectionUtils.makeAccessible(method);
                Object[] args = resolveExecutableArgs(method.getParameters(), method.getGenericParameterTypes());
                ReflectionUtils.invokeMethod(method, bean, args);
            }
        }
    }

    private void initializeBean(String name, Object bean, BeanDefinition def) {
        try {
            for (Method m : effectiveType(bean).getMethods()) {
                if (m.isAnnotationPresent(PostConstruct.class)) {
                    ReflectionUtils.invokeMethod(m, bean);
                }
            }
            if (bean instanceof InitializingBean ib) {
                ib.afterPropertiesSet();
            }
            if (def.getInitMethodName() != null && !def.getInitMethodName().isEmpty()) {
                Method m = effectiveType(bean).getMethod(def.getInitMethodName());
                ReflectionUtils.invokeMethod(m, bean);
            }
        } catch (BeansException e) {
            throw e;
        } catch (Throwable t) {
            throw new BeansException("Failed to initialize bean '" + name + "': " + t.getMessage(), t);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name, Class<T> requiredType) {
        Object bean = getBean(name);
        return adapt(bean, requiredType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> requiredType) {
        String[] names = getBeanNamesForType(requiredType);
        if (names.length == 0) {
            if (requiredType == ApplicationContext.class) return (T) this;
            if (requiredType == Environment.class) return (T) environment;
            throw new NoSuchBeanDefinitionException(requiredType);
        }
        if (names.length == 1) return adapt(getBean(names[0]), requiredType);
        String chosen = choosePrimary(names);
        if (chosen == null) {
            throw new BeansException("No unique bean of type [" + requiredType.getName()
                    + "]: candidates=" + List.of(names));
        }
        return adapt(getBean(chosen), requiredType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> requiredType, String qualifier) {
        if (qualifier != null && containsBean(qualifier)) {
            return adapt(getBean(qualifier), requiredType);
        }
        return getBean(requiredType);
    }

    @Override
    public boolean containsBean(String name) {
        return beanDefinitions.containsKey(name);
    }

    @Override
    public Class<?> getType(String name) {
        BeanDefinition def = beanDefinitions.get(name);
        if (def == null) return null;
        if (targetObjects.containsKey(name)) return def.getBeanClass();
        Object singleton = singletonObjects.get(name);
        if (singleton instanceof SummerProxy) return def.getBeanClass();
        if (singleton != null && !singleton.getClass().getName().contains("$Proxy")) return singleton.getClass();
        return def.getFactoryMethod() != null ? def.getFactoryMethod().getReturnType() : def.getBeanClass();
    }

    @Override
    public String[] getBeanNamesForType(Class<?> type) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, BeanDefinition> e : beanDefinitions.entrySet()) {
            Class<?> bt = getType(e.getKey());
            if (bt != null && type.isAssignableFrom(bt)) result.add(e.getKey());
        }
        return result.toArray(String[]::new);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getBeansOfType(Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        for (String name : getBeanNamesForType(type)) {
            BeanDefinition def = beanDefinitions.get(name);
            if (def.isPrototype()) {
                // 查找时不预先创建原型 Bean
                continue;
            }
            result.put(name, (T) getBean(name));
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        Map<String, T> result = new LinkedHashMap<>();
        for (Map.Entry<String, BeanDefinition> e : beanDefinitions.entrySet()) {
            Class<?> bc = e.getValue().getBeanClass();
            if (bc != null && AnnotationUtils.hasAnnotation(bc, annotationType)) {
                result.put(e.getKey(), (T) getBean(e.getKey()));
            }
        }
        return result;
    }

    @Override
    public Environment getEnvironment() {
        return environment;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        running = false;
        List<String> reverse = new ArrayList<>(destructionOrder);
        Collections.reverse(reverse);
        for (String name : reverse) {
            Object bean = singletonObjects.get(name);
            if (bean == null) continue;
            BeanDefinition def = beanDefinitions.get(name);
            destroyBean(name, bean, def);
        }
        singletonObjects.clear();
        earlySingletonObjects.clear();
        destructionOrder.clear();
    }

    /**
     * 销毁 Bean：调用生命周期销毁回调并释放资源。
     */
    private void destroyBean(String name, Object bean, BeanDefinition def) {
        Object target = targetObjects.getOrDefault(name, bean);
        try {
            for (Method m : target.getClass().getMethods()) {
                if (m.isAnnotationPresent(PreDestroy.class)) {
                    ReflectionUtils.invokeMethod(m, target);
                }
            }
            if (target instanceof DisposableBean db) {
                db.destroy();
            }
            if (def != null && def.getDestroyMethodName() != null && !def.getDestroyMethodName().isEmpty()) {
                try {
                    Method m = bean.getClass().getMethod(def.getDestroyMethodName());
                    ReflectionUtils.invokeMethod(m, bean);
                } catch (NoSuchMethodException ignore) {
                    // 忽略缺失的销毁方法
                }
            }
        } catch (Throwable t) {
            LOG.warning("Error destroying bean '" + name + "': " + t.getMessage());
        }
    }

    // ---- 依赖解析 -------------------------------------------------

    @SuppressWarnings("unchecked")
    /**
     * 解析目标类型的依赖：按类型/名称/限定符查找候选 Bean，缺失时按 required 决定是否抛出异常。
     */
    private Object resolveDependency(Class<?> type, Type genericType, String qualifier, String paramName, boolean required) {
        if (type == ApplicationContext.class) return this;
        if (type == Environment.class) return environment;
        if (type.isArray()) {
            Class<?> element = resolveElementType(genericType, type);
            if (element != null) {
                Map<String, ?> beans = getBeansOfType(element);
                Object array = java.lang.reflect.Array.newInstance(element, beans.size());
                int i = 0;
                for (Object v : beans.values()) {
                    java.lang.reflect.Array.set(array, i++, v);
                }
                return array;
            }
        }
        if (Collection.class.isAssignableFrom(type)) {
            Class<?> element = resolveElementType(genericType, type);
            if (element != null) {
                Map<String, ?> beans = getBeansOfType(element);
                return new ArrayList<>(beans.values());
            }
        }
        String[] names = getBeanNamesForType(type);
        if (names.length == 0) {
            if (!required) return null;
            throw new NoSuchBeanDefinitionException(type);
        }
        String chosen = chooseCandidate(names, qualifier, paramName);
        return getBean(chosen);
    }

    private String chooseCandidate(String[] names, String qualifier, String paramName) {
        if (names.length == 1) return names[0];
        String primary = choosePrimary(names);
        if (primary != null) return primary;
        if (qualifier != null && !qualifier.isEmpty()) {
            for (String n : names) if (n.equals(qualifier)) return n;
        }
        if (paramName != null && !paramName.isEmpty()) {
            for (String n : names) if (n.equals(paramName)) return n;
        }
        throw new BeansException("No unique candidate: " + List.of(names)
                + " (qualifier=" + qualifier + ", paramName=" + paramName + ")");
    }

    private String choosePrimary(String[] names) {
        String primary = null;
        for (String n : names) {
            BeanDefinition def = beanDefinitions.get(n);
            if (def != null && def.isPrimary()) {
                if (primary != null) {
                    throw new BeansException("Multiple @Primary beans for candidates " + List.of(names));
                }
                primary = n;
            }
        }
        return primary;
    }

    private Class<?> resolveElementType(Type genericType, Class<?> containerType) {
        if (containerType.isArray()) return containerType.getComponentType();
        if (genericType instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];
            if (arg instanceof Class<?> c) return c;
        }
        return null;
    }

    /**
     * 解析泛型类型——包括类型变量（如 {@code ServiceImpl<M, T>} 中的 {@code M}）——
     * 通过遍历 bean 的类层级，将其解析为具体的 {@link Class}。当类型无法解析为具体类时
     * 返回 {@code null}，此时调用方回退到擦除类型。
     */
    private Class<?> resolveActualType(Type genericType, Class<?> contextClass) {
        if (genericType instanceof Class<?> c) return c;
        if (genericType instanceof TypeVariable<?> tv) {
            Type resolved = resolveTypeVariable(tv, contextClass);
            if (resolved != null) return resolveActualType(resolved, contextClass);
        }
        if (genericType instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw) {
            return raw;
        }
        return null;
    }

    /**
     * 在给定上下文类中解析类型变量到具体类型。
     */
    private Type resolveTypeVariable(TypeVariable<?> variable, Class<?> contextClass) {
        Class<?> current = contextClass;
        while (current != null && current != Object.class) {
            Type genericSuper = current.getGenericSuperclass();
            if (genericSuper instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> raw) {
                TypeVariable<?>[] params = raw.getTypeParameters();
                Type[] args = pt.getActualTypeArguments();
                for (int i = 0; i < params.length; i++) {
                    if (variable.getGenericDeclaration() == raw
                            && params[i].getName().equals(variable.getName())) {
                        return args[i];
                    }
                }
                current = raw;
            } else if (genericSuper instanceof Class<?> c) {
                current = c;
            } else {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Object resolveValue(String expression, Class<?> targetType) {
        String resolved;
        if (expression != null && expression.contains("${")) {
            resolved = environment.resolvePlaceholders(expression);
        } else if (expression != null && environment.containsProperty(expression.trim())) {
            resolved = environment.getProperty(expression.trim());
        } else {
            resolved = expression;
        }
        return Environment.convert(resolved, targetType);
    }

    private Constructor<?> chooseConstructor(Class<?> clazz) {
        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        if (ctors.length == 1) return ctors[0];
        for (Constructor<?> c : ctors) {
            if (c.isAnnotationPresent(Autowired.class)) return c;
        }
        try {
            return clazz.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new BeansException("No default or @Autowired constructor for " + clazz.getName(), e);
        }
    }

    /** 为已存在定义预实例化一个合成的单例（如 mapper 代理）。 */
    public void registerSingleton(String name, Object bean) {
        singletonObjects.put(name, bean);
        destructionOrder.add(name);
    }

    public void registerBeanDefinition(String name, BeanDefinition def) {
        if (beanDefinitions.containsKey(name)) {
            throw new BeansException("Duplicate bean name '" + name + "': "
                    + beanDefinitions.get(name).getBeanClass() + " vs " + def.getBeanClass());
        }
        beanDefinitions.put(name, def);
    }

    @Override
    public void registerBean(String name, Object bean) {
        if (name == null || name.isEmpty()) {
            throw new BeansException("Cannot register bean with empty name");
        }
        if (bean == null) {
            throw new BeansException("Cannot register null bean under name '" + name + "'");
        }
        if (beanDefinitions.containsKey(name) || singletonObjects.containsKey(name)) {
            throw new BeansException("Bean already registered under name '" + name
                    + "'; unregister it first to replace it");
        }
        BeanDefinition def = new BeanDefinition(name, bean.getClass());
        def.setSynthetic(true);
        def.setInstanceSupplier(() -> bean);
        beanDefinitions.put(name, def);
        singletonObjects.put(name, bean);
        destructionOrder.add(name);
    }

    @Override
    public boolean unregisterBean(String name) {
        BeanDefinition def = beanDefinitions.remove(name);
        Object bean = singletonObjects.remove(name);
        earlySingletonObjects.remove(name);
        targetObjects.remove(name);
        inCreation.remove(name);
        destructionOrder.remove(name);
        if (bean != null) {
            destroyBean(name, bean, def);
        }
        return bean != null || def != null;
    }

    private static String readStereotypeName(Class<?> clazz) {
        for (Annotation a : clazz.getAnnotations()) {
            Class<? extends Annotation> t = a.annotationType();
            if (t == Component.class
                    || AnnotationUtils.hasAnnotation(t, Component.class)) {
                try {
                    Method valueMethod = t.getMethod("value");
                    String v = (String) valueMethod.invoke(a);
                    if (v != null && !v.isEmpty()) return v;
                } catch (ReflectiveOperationException ignore) {
                    // value() 未提供，回退到默认名称
                }
            }
        }
        return null;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T adapt(Object bean, Class<T> requiredType) {
        if (bean == null) return null;
        if (requiredType.isInstance(bean)) return (T) bean;
        throw new BeansException("Bean is not of required type [" + requiredType.getName()
                + "]: actual=" + bean.getClass().getName());
    }

    public static String decapitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}
