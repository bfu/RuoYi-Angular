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
 *
 * @author ruoyi
 */
class DspValidatorTest
{
    private static final String DOCTYPE = "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">";

    private DspValidator validator()
    {
        SqlSessionFactory sqlSessionFactory = new DefaultSqlSessionFactory(new Configuration());
        return new DspValidator(sqlSessionFactory);
    }

    private String mapper(String namespace, String body)
    {
        return DOCTYPE + "<mapper namespace=\"" + namespace + "\">" + body + "</mapper>";
    }

    @Test
    void validMapperPasses()
    {
        String xml = mapper("user_query", "<select id=\"selectAll\" resultType=\"map\">select 1 as id</select>");
        DspValidationResult result = validator().validate("user_query", xml);
        assertTrue(result.isValid(), () -> String.join(",", result.getErrors()));
        assertEquals("user_query", result.getNamespace());
        assertTrue(result.getStatements().contains("selectAll"));
    }

    @Test
    void malformedXmlFails()
    {
        DspValidationResult result = validator().validate("demo",
                "<mapper namespace=\"demo\"><select id=\"a\" resultType=\"map\">select 1</mapper>");
        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    void missingDoctypeFails()
    {
        String xml = "<mapper namespace=\"demo\">"
                + "<select id=\"a\" resultType=\"map\">select 1</select></mapper>";
        DspValidationResult result = validator().validate("demo", xml);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("DOCTYPE")));
    }

    @Test
    void namespaceMismatchFails()
    {
        DspValidationResult result = validator().validate("demo",
                mapper("other", "<select id=\"a\" resultType=\"map\">select 1</select>"));
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("namespace")));
    }

    @Test
    void selectWithoutResultTypeFails()
    {
        DspValidationResult result = validator().validate("demo", mapper("demo", "<select id=\"a\">select 1</select>"));
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("resultType")));
    }

    @Test
    void dollarPlaceholderProducesWarning()
    {
        DspValidationResult result = validator().validate("demo",
                mapper("demo", "<select id=\"a\" resultType=\"map\">select * from ${table}</select>"));
        assertTrue(result.isValid(), () -> String.join(",", result.getErrors()));
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void normalizeNamespaceRewritesRootNamespace()
    {
        String xml = mapper("demo", "<select id=\"a\" resultType=\"map\">select 1</select>");
        String rewritten = validator().normalizeNamespace(xml, "__dsp_test_demo__");
        assertTrue(rewritten.contains("namespace=\"__dsp_test_demo__\""));
    }
}
