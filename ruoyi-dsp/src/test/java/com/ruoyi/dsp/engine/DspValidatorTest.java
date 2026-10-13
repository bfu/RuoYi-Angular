package com.ruoyi.dsp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.Test;

/**
 * {@link DspValidator} 单元测试（纯解析，无数据库依赖）
 * <p>
 * 覆盖：单条语句片段自动包装、namespace 取脚本 ID、语句 id 由平台填为 slug、
 * 内容中的 id 被忽略、多语句拒绝、遗留 mapper 包装剥离与注入告警。
 *
 * @author ruoyi
 */
class DspValidatorTest
{
    /** 脚本 ID：运行时 namespace */
    private static final Long SCRIPT_ID = 12L;

    /** 脚本标识：语句 id */
    private static final String SLUG = "order_query";

    private static final String FRAGMENT = "<select resultType=\"map\">select 1 as id</select>";

    private static final String LEGACY_DOCTYPE = "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">";

    private DspValidator validator()
    {
        SqlSessionFactory sqlSessionFactory = new DefaultSqlSessionFactory(new Configuration());
        return new DspValidator(sqlSessionFactory);
    }

    @Test
    void singleStatementFragmentPasses()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID, SLUG, FRAGMENT);
        assertTrue(result.isValid(), () -> String.join(",", result.getErrors()));
        // namespace 取脚本 ID，语句 id 取脚本 slug
        assertEquals("12", result.getNamespace());
        assertEquals(1, result.getStatements().size());
        assertEquals(SLUG, result.getStatements().get(0));
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void mapperXmlInjectsNamespaceAndStatementId()
    {
        String xml = DspValidator.mapperXml("12", SLUG, FRAGMENT);
        assertTrue(xml.contains("<!DOCTYPE mapper"));
        assertTrue(xml.contains("namespace=\"12\""));
        assertTrue(xml.contains("id=\"order_query\""));
    }

    @Test
    void statementIdInContentIsIgnored()
    {
        String content = "<select id=\"mine\" resultType=\"map\">select 1</select>";
        DspValidationResult result = validator().validate(SCRIPT_ID, SLUG, content);
        assertTrue(result.isValid(), () -> String.join(",", result.getErrors()));
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("id")));

        String xml = DspValidator.mapperXml("12", SLUG, content);
        assertTrue(xml.contains("id=\"order_query\""));
        assertFalse(xml.contains("id=\"mine\""));
    }

    @Test
    void multipleStatementsFails()
    {
        String content = FRAGMENT + "<insert>insert into demo values(1)</insert>";
        DspValidationResult result = validator().validate(SCRIPT_ID, SLUG, content);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("只能包含一条")));
    }

    @Test
    void legacyMapperWrapperIsStripped()
    {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + LEGACY_DOCTYPE
                + "<mapper namespace=\"legacy_slug\"><select id=\"legacy\" resultType=\"map\">select 1</select></mapper>";
        assertTrue(DspValidator.hasWrapper(xml));

        DspValidationResult result = validator().validate(SCRIPT_ID, SLUG, xml);
        assertTrue(result.isValid(), () -> String.join(",", result.getErrors()));
        assertEquals("12", result.getNamespace());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("已自动忽略")));
    }

    @Test
    void malformedXmlFails()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID, SLUG,
                "<select resultType=\"map\">select 1");
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void emptyContentFails()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID, SLUG, "   ");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("内容为空")));
    }

    @Test
    void fragmentWithoutStatementFails()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID, SLUG, "<!-- 只有注释 -->");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("未找到")));
    }

    @Test
    void selectWithoutResultTypeFails()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID, SLUG, "<select>select 1</select>");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("resultType")));
    }

    @Test
    void dollarPlaceholderProducesWarning()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID, SLUG,
                "<select resultType=\"map\">select * from ${table}</select>");
        assertTrue(result.isValid(), () -> String.join(",", result.getErrors()));
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void missingScriptIdOrSlugFails()
    {
        assertFalse(validator().validate(null, SLUG, FRAGMENT).isValid());
        assertFalse(validator().validate(SCRIPT_ID, "  ", FRAGMENT).isValid());
    }
}
