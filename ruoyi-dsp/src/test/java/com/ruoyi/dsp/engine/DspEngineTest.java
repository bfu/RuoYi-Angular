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
 * 覆盖：单条语句片段的动态注册/卸载、语句 id 取脚本 slug、语句分发执行、
 * 事务回滚预览、重复发布不产生重复键异常。
 *
 * @author ruoyi
 */
class DspEngineTest
{
    /** 运行时 namespace：脚本 ID */
    private static final String NAMESPACE = "12";

    /** 语句 id：脚本 slug */
    private static final String SLUG = "demo_user";

    /** 脚本内容仅需一条语句片段（未写 id），mapper 包装与 id 由平台生成 */
    private static final String XML = "<select resultType=\"map\">select id, name from demo_user order by id</select>";

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
    void selectReturnsRowsByStatementId()
    {
        engine.register(NAMESPACE, SLUG, XML);
        assertTrue(engine.hasNamespace(NAMESPACE));

        DspExecutionResult result = engine.execute(NAMESPACE, SLUG, null, false);
        assertEquals("SELECT", result.getSqlCommandType());
        assertNotNull(result.getRows());
        assertEquals(1, result.getRows().size());
        // 返回 Map 的 key 为数据库原始列标签（H2 默认大写），故用 containsValue 断言
        assertTrue(result.getRows().get(0).containsValue("alice"));
    }

    @Test
    void fullStatementIdIsNamespaceDotSlug()
    {
        engine.register(NAMESPACE, SLUG, XML);
        // 完整语句 id = 脚本ID.slug
        DspExecutionResult result = engine.execute(NAMESPACE, NAMESPACE + "." + SLUG, null, false);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void rollbackPreviewDoesNotPersist()
    {
        engine.register(NAMESPACE, SLUG, "<insert>insert into demo_user(id, name) values(#{id}, #{name})</insert>");
        Map<String, Object> params = new HashMap<>();
        params.put("id", 99L);
        params.put("name", "bob");

        DspExecutionResult inserted = engine.execute(NAMESPACE, SLUG, params, true);
        assertEquals("INSERT", inserted.getSqlCommandType());
        assertEquals(1, inserted.getAffectedRows());
        assertTrue(inserted.isRollback());

        // 回滚预览不应落库，仍只有初始的 1 条
        engine.register(NAMESPACE, SLUG, XML);
        DspExecutionResult after = engine.execute(NAMESPACE, SLUG, null, false);
        assertEquals(1, after.getRows().size());
    }

    @Test
    void commitExecutionPersists()
    {
        engine.register(NAMESPACE, SLUG, "<insert>insert into demo_user(id, name) values(#{id}, #{name})</insert>");
        Map<String, Object> params = new HashMap<>();
        params.put("id", 2L);
        params.put("name", "carol");

        DspExecutionResult inserted = engine.execute(NAMESPACE, SLUG, params, false);
        assertEquals(1, inserted.getAffectedRows());

        engine.register(NAMESPACE, SLUG, XML);
        DspExecutionResult after = engine.execute(NAMESPACE, SLUG, null, false);
        assertEquals(2, after.getRows().size());
    }

    @Test
    void reRegisterReplacesStatementsWithoutDuplicateKeyError()
    {
        engine.register(NAMESPACE, SLUG, XML);
        // 重复发布同一 namespace 不应抛出 StrictMap 重复键异常
        engine.register(NAMESPACE, SLUG, XML);
        assertTrue(engine.hasNamespace(NAMESPACE));

        DspExecutionResult result = engine.execute(NAMESPACE, SLUG, null, false);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void unregisterRemovesStatements()
    {
        engine.register(NAMESPACE, SLUG, XML);
        engine.unregister(NAMESPACE);
        assertFalse(engine.hasNamespace(NAMESPACE));
        assertThrows(ServiceException.class, () -> engine.execute(NAMESPACE, SLUG, null, false));
    }

    @Test
    void unknownStatementThrows()
    {
        engine.register(NAMESPACE, SLUG, XML);
        assertThrows(ServiceException.class, () -> engine.execute(NAMESPACE, "missing", null, false));
    }

    @Test
    void contentWithoutStatementThrows()
    {
        // 仅有历史 mapper 包装、无语句时，注册应快速失败而不是静默加载 0 条语句
        assertThrows(ServiceException.class,
                () -> engine.register(NAMESPACE, SLUG, "<mapper namespace=\"legacy\"></mapper>"));
        assertFalse(engine.hasNamespace(NAMESPACE));
    }

    @Test
    void legacyWrappedContentStillRegisters()
    {
        String legacy = "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
                + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">"
                + "<mapper namespace=\"legacy_slug\"><select resultType=\"map\">"
                + "select id, name from demo_user order by id</select></mapper>";
        engine.register(NAMESPACE, SLUG, legacy);

        DspExecutionResult result = engine.execute(NAMESPACE, SLUG, null, false);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void scalarParameterIsUnwrapped()
    {
        engine.register(NAMESPACE, SLUG,
                "<select parameterType=\"long\" resultType=\"map\">select id, name from demo_user where id = #{id}</select>");
        Map<String, Object> params = new HashMap<>();
        params.put("id", 1L);
        // 声明 parameterType="long" 时，引擎应将单元素 Map 解包为标量
        DspExecutionResult result = engine.execute(NAMESPACE, SLUG, params, false);
        assertEquals(1, result.getRows().size());
    }
}
