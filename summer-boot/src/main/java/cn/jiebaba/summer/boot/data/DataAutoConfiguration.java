package cn.jiebaba.summer.boot.data;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.data.datasource.DsInterceptor;
import cn.jiebaba.summer.data.datasource.DsTransactionInterceptor;
import cn.jiebaba.summer.data.datasource.DsTransactionManager;
import cn.jiebaba.summer.data.datasource.DynamicDataSource;
import cn.jiebaba.summer.data.mapper.MapperProxyFactory;
import cn.jiebaba.summer.data.mapper.MapperSupport;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.dialect.Dialect;
import cn.jiebaba.summer.data.support.DataProperties;
import cn.jiebaba.summer.data.support.DataSourceFactory;
import cn.jiebaba.summer.data.support.SqlExecutor;
import cn.jiebaba.summer.data.transaction.TransactionInterceptor;
import cn.jiebaba.summer.data.transaction.TransactionManager;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Auto-configures the data layer. Supports two modes:
 * <ul>
 *   <li><b>Single datasource</b>: {@code summer.datasource.*} (backward compatible)</li>
 *   <li><b>Multi datasource</b>: {@code summer.datasources.<name>.*} with
 *       {@code summer.datasource.default} specifying the primary key.
 *       Enables {@code @DS}/{@code @Master}/{@code @Slave}/{@code @DSTransactional}.</li>
 * </ul>
 */
@Configuration
public class DataAutoConfiguration {

    @Bean
    public DataSource dataSource(Environment env) {
        Map<String, DataSource> sources = buildDataSources(env);
        if (sources.isEmpty()) {
            return DataSourceFactory.lazyDummy();
        }
        if (sources.size() == 1) {
            return sources.values().iterator().next();
        }
        String defaultKey = env.getProperty("summer.datasource.default", "master");
        return new DynamicDataSource(sources, defaultKey);
    }

    @Bean
    public SqlExecutor sqlExecutor(DataSource dataSource, Dialect dialect) {
        return new SqlExecutor(dataSource, dialect);
    }

    @Bean
    public DataProperties dataProperties(Environment env) {
        return DataProperties.from(env);
    }

    @Bean
    public Dialect dialect(Environment env) {
        DataProperties props = DataProperties.from(env);
        return Dialect.detect(props.driver(), props.url());
    }

    @Bean
    public TransactionManager transactionManager(DataSource dataSource) {
        return new TransactionManager(dataSource);
    }

    @Bean
    public TransactionInterceptor transactionInterceptor(TransactionManager tm) {
        return new TransactionInterceptor(tm);
    }

    @Bean
    public DsTransactionManager dsTransactionManager() {
        return new DsTransactionManager();
    }

    @Bean
    public DsTransactionInterceptor dsTransactionInterceptor(DsTransactionManager tm) {
        return new DsTransactionInterceptor(tm);
    }

    @Bean
    public DsInterceptor dsInterceptor() {
        return new DsInterceptor();
    }

    public static Object createMapperProxy(Class<?> mapperInterface, SqlExecutor executor) {
        TableInfo tableInfo = MapperProxyFactory.tableInfoFor(mapperInterface);
        MapperSupport<?> support = new MapperSupport<>(tableInfo, executor);
        return MapperProxyFactory.create(mapperInterface, support);
    }

    /**
     * Builds datasource(s) from configuration. Multi-datasource mode is detected
     * by the presence of {@code summer.datasources.*.url} keys; otherwise falls
     * back to single {@code summer.datasource.*}.
     */
    private Map<String, DataSource> buildDataSources(Environment env) {
        Map<String, DataSource> sources = new LinkedHashMap<>();

        // Multi-datasource: summer.datasources.<name>.url
        TreeSet<String> names = new TreeSet<>();
        for (String key : env.all().keySet()) {
            if (key.startsWith("summer.datasources.") && key.endsWith(".url")) {
                String name = key.substring("summer.datasources.".length(), key.length() - ".url".length());
                names.add(name);
            }
        }
        for (String name : names) {
            sources.put(name, createDataSource(env, "summer.datasources." + name));
        }

        // Single datasource fallback: summer.datasource.*
        if (sources.isEmpty()) {
            DataProperties props = DataProperties.from(env);
            if (props.isConfigured()) {
                sources.put("master", DataSourceFactory.create(env));
            }
        }
        return sources;
    }

    private DataSource createDataSource(Environment env, String prefix) {
        String url = env.getProperty(prefix + ".url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Missing " + prefix + ".url");
        }
        return DataSourceFactory.create(
                env.getProperty(prefix + ".url"),
                env.getProperty(prefix + ".username", ""),
                env.getProperty(prefix + ".password", ""),
                env.getProperty(prefix + ".driver-class-name", ""),
                env.getProperty(prefix + ".pool-size", Integer.class, 8),
                env.getProperty(prefix + ".connection-timeout", Long.class, 30000L),
                env.getProperty(prefix + ".leak-detection-threshold", Long.class, 0L),
                env.getProperty(prefix + ".idle-timeout", Long.class, 600000L),
                env.getProperty(prefix + ".max-lifetime", Long.class, 1800000L),
                env.getProperty(prefix + ".keepalive-query", "SELECT 1"));
    }
}