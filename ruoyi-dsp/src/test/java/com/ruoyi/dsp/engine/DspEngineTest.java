package com.ruoyi.dsp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.mapper.DspDataSourceMapper;

/**
 * {@link DspEngine} 单元测试（H2 内存库）
 * <p>
 * 覆盖：单条语句片段的动态注册/卸载、语句 id 取脚本 slug、语句分发执行、
 * 事务回滚预览、重复发布不产生重复键异常，以及按数据源隔离注册与执行。
 * 默认数据源（{@code 0}）复用主库会话，其余按记录构建独立连接池与会话。
 *
 * @author ruoyi
 */
class DspEngineTest
{
    /** 默认数据源ID（主库） */
    private static final Long DEFAULT_DS = 0L;

    /** 运行时数据源ID */
    private static final Long RUNTIME_DS = 9L;

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
        String jdbcUrl = "jdbc:h2:mem:dsp_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(jdbcUrl);
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
        // 运行时数据源按记录构建连接池，指向同一个 H2 内存库以验证隔离执行
        DspDataSource runtime = new DspDataSource();
        runtime.setDataSourceId(RUNTIME_DS);
        runtime.setName("runtime");
        runtime.setDriverClassName("org.h2.Driver");
        runtime.setUrl(jdbcUrl);
        runtime.setUsername("sa");
        runtime.setPassword("");
        runtime.setStatus("0");
        DspDataSourceMapper mapper = mock(DspDataSourceMapper.class);
        when(mapper.selectDspDataSourceById(RUNTIME_DS)).thenReturn(runtime);
        DspDataSourceManager dataSourceManager = new DspDataSourceManager(sqlSessionFactory, transactionManager, mapper);
        this.engine = new DspEngine(dataSourceManager);
    }

    @Test
    void selectReturnsRowsByStatementId()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        assertTrue(engine.hasNamespace(DEFAULT_DS, NAMESPACE));

        DspExecutionResult result = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, null, false);
        assertEquals("SELECT", result.getSqlCommandType());
        assertNotNull(result.getRows());
        assertEquals(1, result.getRows().size());
        // 返回 Map 的 key 为数据库原始列标签（H2 默认大写），故用 containsValue 断言
        assertTrue(result.getRows().get(0).containsValue("alice"));
    }

    @Test
    void fullStatementIdIsNamespaceDotSlug()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        // 完整语句 id = 脚本ID.slug
        DspExecutionResult result = engine.execute(DEFAULT_DS, NAMESPACE, NAMESPACE + "." + SLUG, null, false);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void rollbackPreviewDoesNotPersist()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG,
                "<insert>insert into demo_user(id, name) values(#{id}, #{name})</insert>");
        Map<String, Object> params = new HashMap<>();
        params.put("id", 99L);
        params.put("name", "bob");

        DspExecutionResult inserted = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, params, true);
        assertEquals("INSERT", inserted.getSqlCommandType());
        assertEquals(1, inserted.getAffectedRows());
        assertTrue(inserted.isRollback());

        // 回滚预览不应落库，仍只有初始的 1 条
        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        DspExecutionResult after = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, null, false);
        assertEquals(1, after.getRows().size());
    }

    @Test
    void commitExecutionPersists()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG,
                "<insert>insert into demo_user(id, name) values(#{id}, #{name})</insert>");
        Map<String, Object> params = new HashMap<>();
        params.put("id", 2L);
        params.put("name", "carol");

        DspExecutionResult inserted = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, params, false);
        assertEquals(1, inserted.getAffectedRows());

        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        DspExecutionResult after = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, null, false);
        assertEquals(2, after.getRows().size());
    }

    @Test
    void reRegisterReplacesStatementsWithoutDuplicateKeyError()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        // 重复发布同一 namespace 不应抛出 StrictMap 重复键异常
        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        assertTrue(engine.hasNamespace(DEFAULT_DS, NAMESPACE));

        DspExecutionResult result = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, null, false);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void unregisterRemovesStatements()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        assertEquals(1, engine.loadedNamespaceCount());
        engine.unregister(DEFAULT_DS, NAMESPACE);
        assertFalse(engine.hasNamespace(DEFAULT_DS, NAMESPACE));
        assertThrows(ServiceException.class, () -> engine.execute(DEFAULT_DS, NAMESPACE, SLUG, null, false));
    }

    @Test
    void unknownStatementThrows()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        assertThrows(ServiceException.class, () -> engine.execute(DEFAULT_DS, NAMESPACE, "missing", null, false));
    }

    @Test
    void contentWithoutStatementThrows()
    {
        // 仅有历史 mapper 包装、无语句时，注册应快速失败而不是静默加载 0 条语句
        assertThrows(ServiceException.class,
                () -> engine.register(DEFAULT_DS, NAMESPACE, SLUG, "<mapper namespace=\"legacy\"></mapper>"));
        assertFalse(engine.hasNamespace(DEFAULT_DS, NAMESPACE));
    }

    @Test
    void legacyWrappedContentStillRegisters()
    {
        String legacy = "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
                + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">"
                + "<mapper namespace=\"legacy_slug\"><select resultType=\"map\">"
                + "select id, name from demo_user order by id</select></mapper>";
        engine.register(DEFAULT_DS, NAMESPACE, SLUG, legacy);

        DspExecutionResult result = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, null, false);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void scalarParameterIsUnwrapped()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG,
                "<select parameterType=\"long\" resultType=\"map\">select id, name from demo_user where id = #{id}</select>");
        Map<String, Object> params = new HashMap<>();
        params.put("id", 1L);
        // 声明 parameterType="long" 时，引擎应将单元素 Map 解包为标量
        DspExecutionResult result = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, params, false);
        assertEquals(1, result.getRows().size());
    }

    @Test
    void pageParamsAcceptNumericStrings()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG,
                "<select resultType=\"map\">select id, name from demo_user order by id limit #{offset}, #{size}</select>");
        Map<String, Object> params = new HashMap<>();
        // 测试抽屉的 JSON、GET /dsp/exec 的 URL 查询串里取值天然是字符串，
        // 而 LIMIT 只接受整数（客户端预编译会拼成 limit '0', '1'），引擎需自行规整
        params.put("offset", "0");
        params.put("size", "1");

        DspExecutionResult result = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, params, false);
        assertEquals(1, result.getRows().size());
        // 占位符取值被规整为整数（H2 对 limit '0', '1' 宽容，故以参数类型为断言）
        assertEquals(0L, params.get("offset"));
        assertEquals(1L, params.get("size"));
    }

    @Test
    void pageParamsRejectNonNumericValue()
    {
        engine.register(DEFAULT_DS, NAMESPACE, SLUG,
                "<select resultType=\"map\">select id from demo_user limit #{offset}, #{size}</select>");
        Map<String, Object> params = new HashMap<>();
        params.put("offset", "0");
        params.put("size", "abc");

        ServiceException error = assertThrows(ServiceException.class,
                () -> engine.execute(DEFAULT_DS, NAMESPACE, SLUG, params, false));
        assertTrue(error.getMessage().contains("必须是整数"));
    }

    @Test
    void numericStringOutsidePageClauseStaysString()
    {
        // 等值条件上的数字串不能被转成数值：否则字符串列会走隐式类型转换，语义与效率都变差
        engine.register(DEFAULT_DS, NAMESPACE, SLUG,
                "<insert>insert into demo_user(id, name) values(#{id}, #{name})</insert>");
        Map<String, Object> row = new HashMap<>();
        row.put("id", 7L);
        row.put("name", "1");
        engine.execute(DEFAULT_DS, NAMESPACE, SLUG, row, false);

        engine.register(DEFAULT_DS, NAMESPACE, SLUG,
                "<select resultType=\"map\">select id, name from demo_user where name = #{name}</select>");
        Map<String, Object> params = new HashMap<>();
        params.put("name", "1");

        DspExecutionResult result = engine.execute(DEFAULT_DS, NAMESPACE, SLUG, params, false);
        assertEquals(1, result.getRows().size());
        assertTrue(result.getRows().get(0).containsValue("1"));
    }

    @Test
    void runtimeDataSourceIsIsolatedFromDefault()
    {
        // 同一个 slug 分别注册到默认库与运行时数据源，互不影响
        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        engine.register(RUNTIME_DS, NAMESPACE, SLUG, XML);
        assertEquals(2, engine.loadedNamespaceCount());

        DspExecutionResult runtimeResult = engine.execute(RUNTIME_DS, NAMESPACE, SLUG, null, false);
        assertEquals(1, runtimeResult.getRows().size());

        engine.unregisterAll(DEFAULT_DS);
        assertFalse(engine.hasNamespace(DEFAULT_DS, NAMESPACE));
        assertTrue(engine.hasNamespace(RUNTIME_DS, NAMESPACE));
    }

    @Test
    void unavailableDataSourceThrows()
    {
        // 未登记的数据源在注册阶段即报错，避免语句落到错误的库
        assertThrows(ServiceException.class, () -> engine.register(404L, NAMESPACE, SLUG, XML));
    }

    @Test
    void hasAnyNamespaceTracksEachDataSource()
    {
        // 用于判断数据源连接池可否回收：仅当该数据源上再无语句时返回 false
        assertFalse(engine.hasAnyNamespace(DEFAULT_DS));

        engine.register(DEFAULT_DS, NAMESPACE, SLUG, XML);
        assertTrue(engine.hasAnyNamespace(DEFAULT_DS));
        assertFalse(engine.hasAnyNamespace(RUNTIME_DS));

        engine.unregister(DEFAULT_DS, NAMESPACE);
        assertFalse(engine.hasAnyNamespace(DEFAULT_DS));
    }
}
