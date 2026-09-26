package cn.jiebaba.summer.boot.data;

import cn.jiebaba.summer.core.annotation.Bean;
import cn.jiebaba.summer.core.annotation.ConditionalOnMissingBean;
import cn.jiebaba.summer.core.annotation.Configuration;
import cn.jiebaba.summer.core.env.Environment;
import cn.jiebaba.summer.data.datasource.DsInterceptor;
import cn.jiebaba.summer.data.datasource.DsTransactionInterceptor;
import cn.jiebaba.summer.data.datasource.DsTransactionManager;
import cn.jiebaba.summer.data.datasource.DynamicDataSource;
import cn.jiebaba.summer.data.mapper.MapperProxyFactory;
import cn.jiebaba.summer.data.mapper.MapperSupport;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.migration.DatabaseMigration;
import cn.jiebaba.summer.data.migration.SchemaMigrator;
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
 * 自动配置数据层。支持两种模式：
 * <ul>
 *   <li><b>单数据源</b>：{@code summer.datasource.*}（向后兼容）</li>
 *   <li><b>多数据源</b>：{@code summer.datasources.<name>.*}，
 *       并以 {@code summer.datasource.default} 指定主数据源。
 *       启用 {@code @DS}/{@code @Master}/{@code @Slave}/{@code @DSTransactional}。</li>
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

    /**
     * 版本化 schema 迁移执行器：收集容器内全部 {@link DatabaseMigration} Bean，
     * 按版本号升序执行未应用者并记入迁移历史表。在 Bean 创建期（容器 refresh 阶段）
     * 执行，先于 Web 服务启动与 ApplicationRunner；无迁移 Bean 时零开销（不建历史表）。
     */
    @Bean
    @ConditionalOnMissingBean
    public SchemaMigrator schemaMigrator(SqlExecutor executor, TransactionManager tm,
                                         cn.jiebaba.summer.core.context.ApplicationContext context) {
        SchemaMigrator migrator = new SchemaMigrator(executor, tm, SchemaMigrator.DEFAULT_HISTORY_TABLE);
        Map<String, DatabaseMigration> migrations = context.getBeansOfType(DatabaseMigration.class);
        if (!migrations.isEmpty()) {
            migrator.migrate(migrations.values());
        }
        return migrator;
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
     * 根据配置构建数据源。若存在 {@code summer.datasources.*.url} 键则判定为多数据源模式；
     * 否则回退到单数据源 {@code summer.datasource.*}。
     */
    private Map<String, DataSource> buildDataSources(Environment env) {
        Map<String, DataSource> sources = new LinkedHashMap<>();

        // 多数据源：summer.datasources.<name>.url
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

        // 单数据源回退：summer.datasource.*
        if (sources.isEmpty()) {
            DataProperties props = DataProperties.from(env);
            if (props.isConfigured()) {
                sources.put("master", DataSourceFactory.create(env));
            }
        }
        return sources;
    }

    private DataSource createDataSource(Environment env, String prefix) {
        DataProperties props = DataProperties.from(env, prefix);
        if (!props.isConfigured()) {
            throw new IllegalStateException("Missing " + prefix + ".url");
        }
        return DataSourceFactory.create(props);
    }
}
