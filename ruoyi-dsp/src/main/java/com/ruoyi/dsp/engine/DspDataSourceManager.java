package com.ruoyi.dsp.engine;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.alibaba.druid.pool.DruidDataSource;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.mapper.DspDataSourceMapper;

/**
 * 数据源运行时会话管理器
 * <p>
 * 按 {@code dataSourceId} 维护「Druid 连接池 + 独立 MyBatis {@link Configuration} + {@link SqlSessionTemplate}
 * + 独立事务管理器」。默认库（{@code 0}）直接复用 Spring 现有的 {@link SqlSessionFactory} 与事务管理器，
 * 行为与未引入数据源管理时完全一致；其余数据源在首次使用时按记录懒加载并缓存。
 * <p>
 * 每个数据源拥有独立的 MyBatis 配置，因此动态脚本语句注册与执行都隔离在该数据源的连接上。
 *
 * @author ruoyi
 */
@Component
public class DspDataSourceManager
{
    private static final Logger log = LoggerFactory.getLogger(DspDataSourceManager.class);

    /** 状态：正常 */
    private static final String STATUS_NORMAL = "0";

    /** 运行时数据源默认连接池参数（按任意驱动设计，不依赖方言校验语句） */
    private static final int POOL_MIN_IDLE = 1;

    private static final int POOL_MAX_ACTIVE = 8;

    /** 取连接的最长等待：库不可用时快速失败，不让请求长时间挂起 */
    private static final int POOL_MAX_WAIT_MILLIS = 10000;

    /** 探活超时（秒），用于 Connection.isValid */
    private static final int VALIDATION_QUERY_TIMEOUT_SECONDS = 3;

    /** 空闲检测线程运行间隔 */
    private static final long EVICTION_RUN_INTERVAL_MILLIS = 30000L;

    /** 空闲连接可被回收前的最短闲置时间（配合 keepAlive，minIdle 以内的连接改为探活保活） */
    private static final long MIN_EVICTABLE_IDLE_MILLIS = 120000L;

    /** keepAlive 探活间隔，需不小于空闲检测间隔 */
    private static final long KEEP_ALIVE_BETWEEN_MILLIS = 60000L;

    /** 运行中主配置，用于复制类型别名等元信息 */
    private final Configuration liveConfiguration;

    /** 主库会话 */
    private final SqlSessionTemplate liveSqlSession;

    /** 主库事务管理器 */
    private final PlatformTransactionManager liveTransactionManager;

    private final DspDataSourceMapper dspDataSourceMapper;

    /** 默认库（主库）会话 */
    private final Holder defaultHolder;

    /** dataSourceId → 会话 */
    private final Map<Long, Holder> holders = new ConcurrentHashMap<>();

    public DspDataSourceManager(SqlSessionFactory sqlSessionFactory, PlatformTransactionManager transactionManager,
            DspDataSourceMapper dspDataSourceMapper)
    {
        this.liveConfiguration = sqlSessionFactory.getConfiguration();
        this.liveSqlSession = new SqlSessionTemplate(sqlSessionFactory);
        this.liveTransactionManager = transactionManager;
        this.dspDataSourceMapper = dspDataSourceMapper;
        this.defaultHolder = new Holder(DspDataSource.DEFAULT_ID, liveConfiguration, liveSqlSession,
                liveTransactionManager, null);
    }

    /**
     * 获取指定数据源的会话；{@code null} 或 {@code 0} 返回默认库，其余懒加载并缓存
     */
    public Holder resolve(Long dataSourceId)
    {
        if (isDefault(dataSourceId))
        {
            return defaultHolder;
        }
        Holder holder = holders.get(dataSourceId);
        if (holder != null)
        {
            return holder;
        }
        synchronized (this)
        {
            holder = holders.get(dataSourceId);
            if (holder != null)
            {
                return holder;
            }
            DspDataSource config = loadConfig(dataSourceId);
            holder = build(config);
            holders.put(dataSourceId, holder);
            return holder;
        }
    }

    /**
     * 会话是否已加载（不含默认库）
     */
    public boolean isLoaded(Long dataSourceId)
    {
        return !isDefault(dataSourceId) && holders.containsKey(dataSourceId);
    }

    /**
     * 销毁指定数据源的会话与连接池（默认库不可销毁）；调用方应在销毁前先卸载引擎语句
     */
    public synchronized void evict(Long dataSourceId)
    {
        if (isDefault(dataSourceId))
        {
            return;
        }
        Holder holder = holders.remove(dataSourceId);
        if (holder != null)
        {
            holder.close();
            log.info("数据源会话已销毁 dataSourceId={}", dataSourceId);
        }
    }

    /**
     * 销毁全部运行时数据源会话（默认库保留）
     */
    public synchronized void evictAll()
    {
        for (Long dataSourceId : holders.keySet())
        {
            Holder holder = holders.remove(dataSourceId);
            if (holder != null)
            {
                holder.close();
            }
        }
    }

    /**
     * 测试连接：按配置临时建池并取一次连接，返回数据库产品信息
     */
    public DspDataSourceTestResult testConnection(DspDataSource config)
    {
        if (config == null)
        {
            return new DspDataSourceTestResult(false, "数据源配置不能为空", 0);
        }
        if (StringUtils.isBlank(config.getDriverClassName()) || StringUtils.isBlank(config.getUrl()))
        {
            return new DspDataSourceTestResult(false, "驱动类名与 JDBC URL 不能为空", 0);
        }
        long start = System.currentTimeMillis();
        DruidDataSource pool = null;
        try
        {
            pool = createPool(config);
            pool.init();
            try (Connection connection = pool.getConnection())
            {
                DatabaseMetaData metaData = connection.getMetaData();
                String info = metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
                return new DspDataSourceTestResult(true, info, System.currentTimeMillis() - start);
            }
        }
        catch (Throwable e)
        {
            return new DspDataSourceTestResult(false, rootMessage(e), System.currentTimeMillis() - start);
        }
        finally
        {
            if (pool != null)
            {
                pool.close();
            }
        }
    }

    /**
     * 打开指定数据源的一条连接（供元数据探测等只读场景使用），由调用方负责关闭
     * <p>
     * 默认库复用 Spring 主数据源；其余数据源按需懒加载运行时连接池并缓存。
     */
    public Connection openConnection(Long dataSourceId) throws SQLException
    {
        DataSource dataSource = resolve(dataSourceId).getDataSource();
        if (dataSource == null)
        {
            dataSource = liveConfiguration.getEnvironment().getDataSource();
        }
        return dataSource.getConnection();
    }

    /**
     * 是否为默认库
     */
    public static boolean isDefault(Long dataSourceId)
    {
        return dataSourceId == null || DspDataSource.DEFAULT_ID.equals(dataSourceId);
    }

    private DspDataSource loadConfig(Long dataSourceId)
    {
        if (dspDataSourceMapper == null)
        {
            throw new ServiceException("数据源不存在：" + dataSourceId);
        }
        DspDataSource config = dspDataSourceMapper.selectDspDataSourceById(dataSourceId);
        if (config == null)
        {
            throw new ServiceException("数据源不存在：" + dataSourceId);
        }
        if (!STATUS_NORMAL.equals(config.getStatus()))
        {
            throw new ServiceException("数据源已停用：" + config.getName());
        }
        return config;
    }

    /**
     * 按记录构建连接池与独立 MyBatis 会话
     */
    private Holder build(DspDataSource config)
    {
        DruidDataSource pool = createPool(config);
        // 使用 SpringManagedTransactionFactory，保证 DataSourceTransactionManager 能管控该连接
        Environment environment = new Environment("dsp-" + config.getDataSourceId(),
                new SpringManagedTransactionFactory(), pool);
        Configuration configuration = new Configuration(environment);
        copyMetadata(configuration);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate sqlSession = new SqlSessionTemplate(sqlSessionFactory);
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(pool);
        log.info("数据源会话已加载 dataSourceId={}, name={}", config.getDataSourceId(), config.getName());
        return new Holder(config.getDataSourceId(), configuration, sqlSession, transactionManager, pool);
    }

    /**
     * 复制主配置的类型别名与驼峰映射，保证脚本中的 resultType 别名与主库一致
     */
    private void copyMetadata(Configuration target)
    {
        try
        {
            target.setMapUnderscoreToCamelCase(liveConfiguration.isMapUnderscoreToCamelCase());
        }
        catch (Exception ignore)
        {
            // 忽略元信息复制失败，使用默认值
        }
        TypeAliasRegistry source = liveConfiguration.getTypeAliasRegistry();
        TypeAliasRegistry aliasRegistry = target.getTypeAliasRegistry();
        Map<String, Class<?>> aliases = source.getTypeAliases();
        if (aliases == null)
        {
            return;
        }
        for (Map.Entry<String, Class<?>> entry : aliases.entrySet())
        {
            try
            {
                aliasRegistry.registerAlias(entry.getKey(), entry.getValue());
            }
            catch (Exception ignore)
            {
                // 别名冲突时忽略，使用默认别名
            }
        }
    }

    /**
     * 构建 Druid 连接池
     * <p>
     * 存活校验统一走 JDBC4 {@link Connection#isValid(int)}（{@link DspJdbc4ValidConnectionChecker}），
     * 不需要方言相关的校验语句，因此在「允许任意 driver」的前提下也能开启探活：
     * <ul>
     * <li>{@code testOnBorrow}：借出前探活。陈旧连接被丢弃并重建，用户不再遇到「长时间无请求后首次请求报错」</li>
     * <li>{@code testWhileIdle + keepAlive + minIdle}：保留少量热连接并周期探活，避免连接被防火墙静默回收，
     * 也省去首次请求的建连延迟；超出 minIdle 的空闲连接仍会按 {@code minEvictableIdleTimeMillis} 释放</li>
     * <li>{@link DspExceptionSorter}：误漏的失效连接在报错时被剔除，不会被反复借出</li>
     * <li>{@code initialSize=0}：仍按需懒建连，数据源不可达不阻塞应用启动</li>
     * </ul>
     */
    private DruidDataSource createPool(DspDataSource config)
    {
        DruidDataSource pool = new DruidDataSource();
        pool.setName("dsp-ds-" + (config.getDataSourceId() == null ? "test" : config.getDataSourceId()));
        pool.setDriverClassName(StringUtils.trim(config.getDriverClassName()));
        pool.setUrl(StringUtils.trim(config.getUrl()));
        pool.setUsername(StringUtils.trim(config.getUsername()));
        pool.setPassword(config.getPassword());
        // 不预设连接：数据源不可达时不阻塞启动，失败在真正执行时暴露
        pool.setInitialSize(0);
        pool.setMinIdle(POOL_MIN_IDLE);
        pool.setMaxActive(POOL_MAX_ACTIVE);
        pool.setMaxWait(POOL_MAX_WAIT_MILLIS);
        // 任意 driver 无法预置校验语句，改用 JDBC4 Connection.isValid 探活
        pool.setValidConnectionChecker(new DspJdbc4ValidConnectionChecker());
        pool.setExceptionSorter(new DspExceptionSorter());
        pool.setValidationQueryTimeout(VALIDATION_QUERY_TIMEOUT_SECONDS);
        pool.setTestOnBorrow(true);
        pool.setTestWhileIdle(true);
        pool.setTestOnReturn(false);
        pool.setKeepAlive(true);
        pool.setKeepAliveBetweenTimeMillis(KEEP_ALIVE_BETWEEN_MILLIS);
        pool.setTimeBetweenEvictionRunsMillis(EVICTION_RUN_INTERVAL_MILLIS);
        pool.setMinEvictableIdleTimeMillis(MIN_EVICTABLE_IDLE_MILLIS);
        pool.setPoolPreparedStatements(false);
        // 沿用 Druid 默认：建连失败不退出创建线程，数据库恢复后连接池可自愈
        pool.setFailFast(false);
        pool.setBreakAfterAcquireFailure(false);
        return pool;
    }

    private String rootMessage(Throwable throwable)
    {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current)
        {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtils.isBlank(message) ? current.getClass().getSimpleName() : message;
    }

    /**
     * 单个数据源的运行时会话
     */
    public static class Holder
    {
        private final Long dataSourceId;

        private final Configuration configuration;

        private final SqlSessionTemplate sqlSession;

        private final PlatformTransactionManager transactionManager;

        /** 运行时连接池，默认库为 null */
        private final DruidDataSource pool;

        Holder(Long dataSourceId, Configuration configuration, SqlSessionTemplate sqlSession,
                PlatformTransactionManager transactionManager, DruidDataSource pool)
        {
            this.dataSourceId = dataSourceId;
            this.configuration = configuration;
            this.sqlSession = sqlSession;
            this.transactionManager = transactionManager;
            this.pool = pool;
        }

        public Long getDataSourceId()
        {
            return dataSourceId;
        }

        public Configuration getConfiguration()
        {
            return configuration;
        }

        public SqlSessionTemplate getSqlSession()
        {
            return sqlSession;
        }

        public PlatformTransactionManager getTransactionManager()
        {
            return transactionManager;
        }

        public DataSource getDataSource()
        {
            return pool;
        }

        private void close()
        {
            if (pool != null)
            {
                pool.close();
            }
        }
    }
}
