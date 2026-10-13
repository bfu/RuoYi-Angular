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
 * 覆盖：语句片段自动包装、遗留 mapper 包装自动剥离、语句完整性、resultType 与注入告警。
 *
 * @author ruoyi
 */
class DspValidatorTest
{
    private static final Long SCRIPT_ID = 12L;

    private static final String DOCTYPE = "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">";

    private DspValidator validator()
    {
        SqlSessionFactory sqlSessionFactory = new DefaultSqlSessionFactory(new Configuration());
        return new DspValidator(sqlSessionFactory);
    }

    @Test
    void statementFragmentPasses()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID,
                "<select id=\"selectAll\" resultType=\"map\">select 1 as id</select>");
        assertTrue(result.isValid(), () -> String.join(",", result.getErrors()));
        // namespace 由脚本 ID 生成，与 slug 无关
        assertEquals("12", result.getNamespace());
        assertTrue(result.getStatements().contains("selectAll"));
    }

    @Test
    void legacyMapperWrapperIsStripped()
    {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + DOCTYPE
                + "<mapper namespace=\"legacy_slug\"><select id=\"a\" resultType=\"map\">select 1</select></mapper>";
        assertTrue(DspValidator.hasWrapper(xml));

        DspValidationResult result = validator().validate(SCRIPT_ID, xml);
        assertTrue(result.isValid(), () -> String.join(",", result.getErrors()));
        assertEquals("12", result.getNamespace());
        assertTrue(result.getWarnings().stream().anyMatch(warning -> warning.contains("已自动忽略")));
    }

    @Test
    void malformedXmlFails()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID,
                "<select id=\"a\" resultType=\"map\">select 1");
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void emptyContentFails()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID, "   ");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("内容为空")));
    }

    @Test
    void fragmentWithoutStatementFails()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID, "<!-- 只有注释 -->");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("未找到任何")));
    }

    @Test
    void selectWithoutResultTypeFails()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID, "<select id=\"a\">select 1</select>");
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("resultType")));
    }

    @Test
    void dollarPlaceholderProducesWarning()
    {
        DspValidationResult result = validator().validate(SCRIPT_ID,
                "<select id=\"a\" resultType=\"map\">select * from ${table}</select>");
        assertTrue(result.isValid(), () -> String.join(",", result.getErrors()));
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void wrapBuildsMapperXmlWithScriptIdNamespace()
    {
        String xml = DspValidator.wrap(DspValidator.namespaceOf(SCRIPT_ID),
                "<select id=\"a\" resultType=\"map\">select 1</select>");
        assertTrue(xml.contains("<!DOCTYPE mapper"));
        assertTrue(xml.contains("namespace=\"12\""));
        assertTrue(xml.contains("<select id=\"a\""));
        assertFalse(DspValidator.hasWrapper(DspValidator.unwrap(xml)));
    }
}
