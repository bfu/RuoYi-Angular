package com.ruoyi.dsp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.mapper.DspDataSourceMapper;

/**
 * {@link DspDataSourceManager} 运行时连接池配置与陈旧连接处理测试
 * <p>
 * 目标问题：长时间无请求后，池中连接已被防火墙或数据库单方面断开，
 * 首次请求把陈旧连接交给调用方而报错。此处验证连接池已开启探活，且陈旧连接会被剔除重建。
 *
 * @author ruoyi
 */
class DspDataSourceManagerTest
{
    private static final Long RUNTIME_DS = 9L;

    private DspDataSourceManager manager;

    @BeforeEach
    void setUp()
    {
        String jdbcUrl = "jdbc:h2:mem:dsp_pool_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        JdbcDataSource primary = new JdbcDataSource();
        primary.setURL(jdbcUrl);
        primary.setUser("sa");
        primary.setPassword("");
        Environment environment = new Environment("test", new SpringManagedTransactionFactory(), primary);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(new Configuration(environment));

        DspDataSource config = new DspDataSource();
        config.setDataSourceId(RUNTIME_DS);
        config.setName("runtime");
        config.setDriverClassName("org.h2.Driver");
        config.setUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword("");
        config.setStatus("0");
        DspDataSourceMapper mapper = mock(DspDataSourceMapper.class);
        when(mapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(config);
        when(mapper.selectDspDataSourceById(404L)).thenReturn(null);

        manager = new DspDataSourceManager(sqlSessionFactory, new DataSourceTransactionManager(primary), mapper);
    }

    @Test
    void runtimePoolEnablesConnectionValidation()
    {
        DruidDataSource pool = runtimePool();
        try
        {
            // 不依赖方言校验语句也能探活：由 JDBC4 校验器承接
            assertInstanceOf(DspJdbc4ValidConnectionChecker.class, pool.getValidConnectionChecker());
            assertInstanceOf(DspExceptionSorter.class, pool.getExceptionSorter());
            assertEquals(3, pool.getValidationQueryTimeout());

            assertTrue(pool.isTestOnBorrow(), "借出前必须探活，否则陈旧连接会被交给调用方");
            assertTrue(pool.isTestWhileIdle(), "空闲时必须探活，坏连接由销毁线程回收");
            assertTrue(pool.isKeepAlive(), "需要保活热连接，避免长时间空闲后被防火墙静默回收");
            assertEquals(1, pool.getMinIdle());
            assertEquals(10_000, pool.getMaxWait());
            assertFalse(pool.isBreakAfterAcquireFailure(),
                    "建连失败不能退出创建线程，否则数据库恢复后连接池无法自愈");
        }
        finally
        {
            pool.close();
        }
    }

    @Test
    void borrowSucceedsOnLiveDataSource() throws Exception
    {
        DruidDataSource pool = runtimePool();
        try (Connection connection = pool.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("select 1"))
        {
            // 开启探活后正常借出不能被校验器干扰
            assertTrue(resultSet.next());
        }
        finally
        {
            pool.close();
        }
    }

    @Test
    void staleConnectionIsDiscardedAndRebuiltOnBorrow() throws Exception
    {
        DruidDataSource pool = runtimePool();
        try
        {
            // 预热：让池中留下一条空闲连接
            DruidPooledConnection pooled = pool.getConnection();
            Connection raw = pooled.getConnectionHolder().getConnection();
            pooled.close();
            // 模拟连接被防火墙/数据库单方面断开：物理连接已关闭，池中记录仍在
            raw.close();
            long discardedBefore = pool.getDiscardCount();

            try (Connection connection = pool.getConnection();
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("select 1"))
            {
                // 借出时应探活识别出陈旧连接并重建，调用方拿到的是可用连接
                assertTrue(resultSet.next());
            }

            assertTrue(pool.getDiscardCount() > discardedBefore, "陈旧连接应被探活识别并丢弃");
        }
        finally
        {
            pool.close();
        }
    }

    private DruidDataSource runtimePool()
    {
        return (DruidDataSource) manager.resolve(RUNTIME_DS).getDataSource();
    }
}
