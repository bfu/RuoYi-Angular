package com.ruoyi.dsp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import com.ruoyi.common.exception.ServiceException;

/**
 * {@link DspEngine} 单元测试（H2 内存库）
 * <p>
 * 覆盖：动态注册/卸载、语句分发执行、事务回滚预览、重复发布不产生重复键异常。
 *
 * @author ruoyi
 */
class DspEngineTest
{
    private static final String NAMESPACE = "demo_user";

    private static final String XML = "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">"
            + "<mapper namespace=\"" + NAMESPACE + "\">"
            + "<select id=\"selectAll\" resultType=\"map\">select id, name from demo_user order by id</select>"
            + "<select id=\"selectById\" parameterType=\"long\" resultType=\"map\">select id, name from demo_user where id = #{id}</select>"
            + "<insert id=\"insertUser\">insert into demo_user(id, name) values(#{id}, #{name})</insert>"
            + "<update id=\"updateName\">update demo_user set name = #{name} where id = #{id}</update>"
            + "<delete id=\"deleteUser\">delete from demo_user where id = #{id}</delete>"
            + "</mapper>";

    private DspEngine engine;

    @BeforeEach
    void setUp() throws Exception
    {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:dsp_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute("create table demo_user(id bigint primary key, name varchar(64))");
            statement.execute("insert into demo_user(id, name) values(1, 'alice')");
        }
        // 与生产一致：使用 SpringManagedTransactionFactory，保证参与 Spring 事务
        Environment environment = new Environment("test", new SpringManagedTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        this.engine = new DspEngine(sqlSessionFactory, transactionManager);
    }

    @Test
    void selectReturnsRows()
    {
        engine.register(NAMESPACE, XML);
        assertTrue(engine.hasNamespace(NAMESPACE));

        DspExecutionResult result = engine.execute(NAMESPACE, "selectAll", null, false);
        assertEquals("SELECT", result.getSqlCommandType());
        assertNotNull(result.getRows());
        assertEquals(1, result.getRows().size());
        // 返回 Map 的 key 为数据库原始列标签（H2 默认大写），故用 containsValue 断言
        assertTrue(result.getRows().get(0).containsValue("alice"));
    }

    @Test
    void rollbackPreviewDoesNotPersist()
    {
        engine.register(NAMESPACE, XML);
        Map<String, Object> params = new HashMap<>();
        params.put("id", 99L);
        params.put("name", "bob");

        DspExecutionResult inserted = engine.execute(NAMESPACE, "insertUser", params, true);
        assertEquals("INSERT", inserted.getSqlCommandType());
        assertEquals(1, inserted.getAffectedRows());
        assertTrue(inserted.isRollback());

        // 回滚预览不应落库，仍只有初始的 1 条
        DspExecutionResult after = engine.execute(NAMESPACE, "selectAll", null, false);
        assertEquals(1, after.getRows().size());
    }

    @Test
    void commitExecutionPersists()
    {
        engine.register(NAMESPACE, XML);
        Map<String, Object> params = new HashMap<>();
        params.put("id", 2L);
        params.put("name", "carol");

        DspExecutionResult inserted = engine.execute(NAMESPACE, "insertUser", params, false);
        assertEquals(1, inserted.getAffectedRows());

        DspExecutionResult after = engine.execute(NAMESPACE, "selectAll", null, false);
        assertEquals(2, after.getRows().size());
    }

    @Test
    void reRegisterReplacesStatementsWithoutDuplicateKeyError()
    {
        engine.register(NAMESPACE, XML);
        // 重复发布同一 namespace 不应抛出 StrictMap 重复键异常
        engine.register(NAMESPACE, XML);
        assertTrue(engine.hasNamespace(NAMESPACE));

        DspExecutionResult result = engine.execute(NAMESPACE, "selectAll", null, false);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void unregisterRemovesStatements()
    {
        engine.register(NAMESPACE, XML);
        engine.unregister(NAMESPACE);
        assertFalse(engine.hasNamespace(NAMESPACE));
        assertThrows(ServiceException.class, () -> engine.execute(NAMESPACE, "selectAll", null, false));
    }

    @Test
    void unknownStatementThrows()
    {
        engine.register(NAMESPACE, XML);
        assertThrows(ServiceException.class, () -> engine.execute(NAMESPACE, "missing", null, false));
    }

    @Test
    void scalarParameterIsUnwrapped()
    {
        engine.register(NAMESPACE, XML);
        Map<String, Object> params = new HashMap<>();
        params.put("id", 1L);
        // selectById 声明 parameterType="long"，引擎应将单元素 Map 解包为标量
        DspExecutionResult result = engine.execute(NAMESPACE, "selectById", params, false);
        assertEquals(1, result.getRows().size());
    }
}
