package com.ruoyi.dsp.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.Test;
import com.ruoyi.dsp.engine.DspValidationResult;
import com.ruoyi.dsp.engine.DspValidator;
import com.ruoyi.dsp.metadata.DspColumnInfo;

/**
 * {@link DspScriptGenerator} 单元测试（纯逻辑，无数据库依赖）
 * <p>
 * 覆盖：查询拆两条（查询所有/翻页查询）、新增拆两条（批量添加/单条添加）在内的 6 条脚本、
 * 默认脚本名称与标识、自增主键不参与新增与 SET、翻页方言随数据库产品切换，
 * 以及生成内容能被 {@link DspValidator} 校验通过（即可被 MyBatis 正常解析）。
 *
 * @author ruoyi
 */
class DspScriptGeneratorTest
{
    private static final String TABLE_NAME = "sys_user";

    private static final String TABLE_COMMENT = "用户信息";

    private final DspScriptGenerator generator = new DspScriptGenerator();

    private List<DspGeneratedScript> generate(String databaseProduct)
    {
        return generator.generate(TABLE_NAME, TABLE_COMMENT, columns(), databaseProduct);
    }

    /** user_id 自增主键 + user_name 必填 + create_time */
    private List<DspColumnInfo> columns()
    {
        return List.of(column("user_id", "userId", true, false, true, 1),
                column("user_name", "userName", false, true, false, 2),
                column("create_time", "createTime", false, false, false, 3));
    }

    private DspColumnInfo column(String name, String field, boolean primaryKey, boolean required,
            boolean autoIncrement, int ordinal)
    {
        DspColumnInfo column = new DspColumnInfo();
        column.setColumnName(name);
        column.setJavaField(field);
        column.setJavaType("String");
        column.setPrimaryKey(primaryKey);
        column.setRequired(required);
        column.setAutoIncrement(autoIncrement);
        column.setOrdinal(ordinal);
        return column;
    }

    private DspGeneratedScript scriptOf(List<DspGeneratedScript> scripts, String action)
    {
        return scripts.stream().filter(item -> action.equals(item.getAction())).findFirst().orElseThrow();
    }

    @Test
    void generatesSixIndependentCrudScripts()
    {
        List<DspGeneratedScript> scripts = generate("MySQL");
        assertEquals(6, scripts.size());
        assertEquals(List.of("查询所有", "翻页查询", "批量添加", "单条添加", "修改", "删除"),
                scripts.stream().map(DspGeneratedScript::getActionName).toList());
        assertEquals(List.of("SELECT", "SELECT", "INSERT", "INSERT", "UPDATE", "DELETE"),
                scripts.stream().map(DspGeneratedScript::getSqlCommandType).toList());
    }

    @Test
    void scriptNameAndSlugHaveDefaults()
    {
        List<DspGeneratedScript> scripts = generate("MySQL");
        assertEquals("sysUserSelectAll", scriptOf(scripts, "selectAll").getSlug());
        assertEquals("sysUserSelectPage", scriptOf(scripts, "selectPage").getSlug());
        assertEquals("sysUserInsertBatch", scriptOf(scripts, "insertBatch").getSlug());
        assertEquals("sysUserInsertOne", scriptOf(scripts, "insertOne").getSlug());
        assertEquals("sysUserUpdate", scriptOf(scripts, "update").getSlug());
        assertEquals("sysUserDelete", scriptOf(scripts, "delete").getSlug());

        // 名称取表注释
        assertEquals("用户信息查询所有", scriptOf(scripts, "selectAll").getName());
        assertEquals("用户信息批量添加", scriptOf(scripts, "insertBatch").getName());
        assertEquals("用户信息删除", scriptOf(scripts, "delete").getName());
    }

    @Test
    void tableWithoutCommentFallsBackToTableName()
    {
        List<DspGeneratedScript> scripts = generator.generate(TABLE_NAME, null, columns(), "MySQL");
        assertEquals("sys_user查询所有", scriptOf(scripts, "selectAll").getName());
    }

    @Test
    void queryScriptsSelectCamelCaseAliasesAndOrderByPrimaryKey()
    {
        String content = scriptOf(generate("MySQL"), "selectAll").getContent();
        assertTrue(content.contains("<select resultType=\"java.util.Map\">"));
        assertTrue(content.contains("select user_id as userId,"));
        assertTrue(content.contains("create_time as createTime"));
        assertTrue(content.contains("from sys_user"));
        // 含自增主键时按最新在前排序
        assertTrue(content.contains("order by user_id desc"));
    }

    @Test
    void pageScriptFollowsDatabaseDialect()
    {
        assertTrue(scriptOf(generate("MySQL"), "selectPage").getContent().contains("limit #{offset}, #{size}"));
        assertTrue(scriptOf(generate("PostgreSQL"), "selectPage").getContent().contains("limit #{size} offset #{offset}"));
        assertTrue(scriptOf(generate("Oracle"), "selectPage").getContent()
                .contains("offset #{offset} rows fetch next #{size} rows only"));
    }

    @Test
    void autoIncrementColumnIsExcludedFromInsertButKeptInCondition()
    {
        List<DspGeneratedScript> scripts = generate("MySQL");
        String insertOne = scriptOf(scripts, "insertOne").getContent();
        assertTrue(insertOne.contains("insert into sys_user ("));
        assertTrue(insertOne.contains("values (#{userName}, #{createTime})"));
        assertFalse(insertOne.contains("#{userId}"));

        String insertBatch = scriptOf(scripts, "insertBatch").getContent();
        assertTrue(insertBatch.contains("<foreach collection=\"list\" item=\"item\" separator=\",\">"));
        assertTrue(insertBatch.contains("(#{item.userName}, #{item.createTime})"));
    }

    @Test
    void updateAndDeleteLocateByPrimaryKey()
    {
        List<DspGeneratedScript> scripts = generate("MySQL");
        String update = scriptOf(scripts, "update").getContent();
        assertTrue(update.contains("update sys_user"));
        assertTrue(update.contains("set user_name = #{userName}"));
        assertTrue(update.contains("where user_id = #{userId}"));

        String delete = scriptOf(scripts, "delete").getContent();
        assertTrue(delete.contains("delete from sys_user"));
        assertTrue(delete.contains("where user_id = #{userId}"));
    }

    @Test
    void generatedContentPassesValidator()
    {
        SqlSessionFactory sqlSessionFactory = new DefaultSqlSessionFactory(new Configuration());
        DspValidator validator = new DspValidator(sqlSessionFactory);
        long scriptId = 1L;
        for (DspGeneratedScript script : generate("MySQL"))
        {
            DspValidationResult result = validator.validate(scriptId++, script.getSlug(), script.getContent());
            assertTrue(result.isValid(), () -> script.getSlug() + " -> " + String.join("；", result.getErrors()));
            assertTrue(result.getWarnings().isEmpty(), () -> String.join("；", result.getWarnings()));
        }
    }

    @Test
    void tableNameIsSanitizedIntoSlug()
    {
        List<DspGeneratedScript> scripts = generator.generate("1-order$item", "订单", columns(), "MySQL");
        // 非字母数字被剔除、以数字开头时补前缀字母，保证满足 dsp_script.slug 的校验规则
        String slug = scriptOf(scripts, "selectAll").getSlug();
        assertTrue(slug.matches("^[a-zA-Z][a-zA-Z0-9_-]*$"), slug);
        assertTrue(slug.startsWith("t1"), slug);
        assertTrue(slug.endsWith("SelectAll"), slug);
    }

    @Test
    void upperCaseTableNameProducesReadableSlug()
    {
        List<DspGeneratedScript> scripts = generator.generate("SYS_USER", null, columns(), "MySQL");
        assertEquals("sysUserSelectAll", scriptOf(scripts, "selectAll").getSlug());
    }

    @Test
    void emptyColumnsProduceNoScripts()
    {
        assertTrue(generator.generate(TABLE_NAME, TABLE_COMMENT, List.of(), "MySQL").isEmpty());
        assertTrue(generator.generate("  ", TABLE_COMMENT, columns(), "MySQL").isEmpty());
    }
}
