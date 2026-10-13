package com.ruoyi.dsp.engine;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import com.ruoyi.common.utils.StringUtils;

/**
 * 动态脚本校验器
 * <p>
 * 脚本内容仅需编写**一条**语句片段（{@code <select>/<insert>/<update>/<delete>}），且**不写 id**：
 * XML 声明、DOCTYPE、{@code <mapper>} 标签与 namespace 由平台生成（namespace 取脚本 ID），
 * 语句 id 由平台统一填为脚本 slug，即完整语句 id 为 {@code 脚本ID.slug}。
 * 本类负责：XML 合法性、语句声明完整性，以及基于临时 {@link Configuration} 的
 * MyBatis 语义校验；校验过程不污染运行中的主配置。
 *
 * @author ruoyi
 */
@Component
public class DspValidator
{
    private static final Logger log = LoggerFactory.getLogger(DspValidator.class);

    /**
     * MyBatis Mapper 固定头部（XML 声明 + DOCTYPE），由平台生成。
     * MyBatis 依赖 DOCTYPE 定位本地 DTD（内部解析，不访问外网）。
     */
    private static final String MAPPER_PROLOG = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" "
            + "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n";

    /** 历史内容或手工粘贴的 XML 声明、DOCTYPE、{@code <mapper>} 包装，加载前自动剥离 */
    private static final Pattern XML_DECLARATION = Pattern.compile("(?is)^\\s*<\\?xml.*?\\?>");

    private static final Pattern DOCTYPE_DECLARATION = Pattern.compile("(?is)<!DOCTYPE[^>]*>");

    private static final Pattern MAPPER_OPEN_TAG = Pattern.compile("(?is)^\\s*<mapper\\b[^>]*>");

    private static final Pattern MAPPER_CLOSE_TAG = Pattern.compile("(?is)</mapper\\s*>\\s*$");

    /** 内容中自行书写的语句 id（平台会忽略并统一使用脚本 slug） */
    private static final Pattern STATEMENT_ID_ATTRIBUTE = Pattern
            .compile("(?is)<(select|insert|update|delete)\\b[^>]*\\bid\\s*=");

    /** 运行中的 MyBatis 配置，用于复用类型别名等元信息 */
    private final Configuration liveConfiguration;

    public DspValidator(SqlSessionFactory sqlSessionFactory)
    {
        this.liveConfiguration = sqlSessionFactory.getConfiguration();
    }

    /**
     * 剥离 XML 声明、DOCTYPE 与 {@code <mapper>} 包装，仅保留语句片段
     */
    public static String unwrap(String content)
    {
        if (StringUtils.isBlank(content))
        {
            return "";
        }
        String body = XML_DECLARATION.matcher(content).replaceFirst("");
        body = DOCTYPE_DECLARATION.matcher(body).replaceAll("");
        body = MAPPER_OPEN_TAG.matcher(body).replaceFirst("");
        body = MAPPER_CLOSE_TAG.matcher(body).replaceFirst("");
        return body.trim();
    }

    /**
     * 内容中是否残留平台不再需要的包装（用于给出提示）
     */
    public static boolean hasWrapper(String content)
    {
        if (StringUtils.isBlank(content))
        {
            return false;
        }
        return XML_DECLARATION.matcher(content).lookingAt()
                || DOCTYPE_DECLARATION.matcher(content).find()
                || MAPPER_OPEN_TAG.matcher(content).lookingAt();
    }

    /**
     * 内容中是否自行书写了语句 id（平台会忽略）
     */
    public static boolean hasStatementId(String content)
    {
        return StringUtils.isNotBlank(content) && STATEMENT_ID_ATTRIBUTE.matcher(content).find();
    }

    /**
     * 组装最终 Mapper XML：补 xml 声明、DOCTYPE 与 {@code <mapper>}（namespace 取脚本 ID），
     * 并把语句 id 统一填为脚本 slug（内容中的 id 一律忽略）。
     *
     * @param namespace   运行时 namespace（脚本 ID）
     * @param statementId 语句 id（脚本 slug）
     * @param content     语句片段
     */
    public static String mapperXml(String namespace, String statementId, String content)
    {
        String xml = MAPPER_PROLOG + "<mapper namespace=\"" + namespace + "\">\n" + unwrap(content) + "\n</mapper>";
        try
        {
            Document document = parseXml(xml);
            Element root = document.getDocumentElement();
            Element statement = firstStatement(root);
            if (statement == null)
            {
                // 无语句，交由调用方校验/注册时报错
                return xml;
            }
            statement.setAttribute("id", statementId);
            // 只序列化 mapper 元素，xml 声明与 DOCTYPE 统一沿用平台固定头部
            return MAPPER_PROLOG + serialize(root);
        }
        catch (Exception e)
        {
            // 解析失败时返回原始包装，交由 MyBatis 或校验逻辑给出可读错误
            log.debug("组装 Mapper XML 失败，返回原始包装", e);
            return xml;
        }
    }

    /**
     * 校验脚本内容
     *
     * @param scriptId 脚本 ID（作为运行时 namespace）
     * @param slug     脚本标识（作为语句 id）
     * @param content  语句片段（一条语句，无需写 id）
     * @return 校验结果
     */
    public DspValidationResult validate(Long scriptId, String slug, String content)
    {
        DspValidationResult result = new DspValidationResult();
        if (scriptId == null)
        {
            result.addError("脚本 ID 不能为空");
            return result;
        }
        if (StringUtils.isBlank(slug))
        {
            result.addError("脚本标识(slug)不能为空");
            return result;
        }
        if (StringUtils.isBlank(content))
        {
            result.addError("脚本内容为空");
            return result;
        }

        String namespace = String.valueOf(scriptId);
        result.setNamespace(namespace);
        if (hasWrapper(content))
        {
            result.addWarning("已自动忽略 <?xml>、DOCTYPE 与 <mapper> 包装，仅需编写一条语句");
        }
        if (hasStatementId(content))
        {
            result.addWarning("语句 id 由平台按脚本标识(slug)填充，内容中书写的 id 已忽略");
        }

        String body = unwrap(content);
        if (StringUtils.isBlank(body))
        {
            result.addError("未找到 <select>/<insert>/<update>/<delete> 语句");
            return result;
        }

        String xml = mapperXml(namespace, slug, body);
        Document document;
        try
        {
            document = parseXml(xml);
        }
        catch (Exception e)
        {
            result.addError("XML 格式错误：" + rootMessage(e));
            return result;
        }

        Element root = document.getDocumentElement();
        List<Element> statements = statementElements(root);
        if (statements.isEmpty())
        {
            result.addError("未找到 <select>/<insert>/<update>/<delete> 语句");
        }
        else if (statements.size() > 1)
        {
            result.addError("一个脚本只能包含一条语句，当前为 " + statements.size() + " 条");
        }
        else
        {
            Element statement = statements.get(0);
            // 实际执行的语句 id 即脚本 slug
            result.getStatements().add(slug);
            if ("select".equalsIgnoreCase(statement.getNodeName())
                    && StringUtils.isBlank(statement.getAttribute("resultType"))
                    && StringUtils.isBlank(statement.getAttribute("resultMap")))
            {
                result.addError("<select> 必须指定 resultType 或 resultMap");
            }
        }

        if (content.contains("${"))
        {
            result.addWarning("检测到 ${} 原始拼接，存在 SQL 注入风险，请确认参数来源可信");
        }

        // 基于临时 Configuration 做 MyBatis 语义校验（复用主配置类型别名）；已有错误时不再叠加
        if (result.isValid())
        {
            try
            {
                Configuration scratch = newScratchConfiguration();
                XMLMapperBuilder builder = new XMLMapperBuilder(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), scratch,
                        namespace + "-validate.xml", scratch.getSqlFragments());
                builder.parse();
            }
            catch (Exception e)
            {
                result.addError("MyBatis 解析失败：" + rootMessage(e));
            }
        }
        return result;
    }

    /**
     * 取根节点下第一条语句元素
     */
    private static Element firstStatement(Element root)
    {
        List<Element> statements = statementElements(root);
        return statements.isEmpty() ? null : statements.get(0);
    }

    /**
     * 取根节点下的全部语句元素
     */
    private static List<Element> statementElements(Element root)
    {
        List<Element> statements = new ArrayList<>();
        if (root == null)
        {
            return statements;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && isStatementNode(node.getNodeName()))
            {
                statements.add((Element) node);
            }
        }
        return statements;
    }

    private static boolean isStatementNode(String nodeName)
    {
        return "select".equalsIgnoreCase(nodeName) || "insert".equalsIgnoreCase(nodeName)
                || "update".equalsIgnoreCase(nodeName) || "delete".equalsIgnoreCase(nodeName);
    }

    /**
     * 构造一个独立 Configuration，并复制主配置的类型别名，保证 resultType 等别名可解析
     */
    private Configuration newScratchConfiguration()
    {
        Configuration scratch = new Configuration();
        TypeAliasRegistry source = liveConfiguration.getTypeAliasRegistry();
        TypeAliasRegistry target = scratch.getTypeAliasRegistry();
        Map<String, Class<?>> aliases = source.getTypeAliases();
        if (aliases != null)
        {
            for (Map.Entry<String, Class<?>> entry : aliases.entrySet())
            {
                try
                {
                    target.registerAlias(entry.getKey(), entry.getValue());
                }
                catch (Exception ignore)
                {
                    // 别名冲突时忽略，使用默认别名
                }
            }
        }
        return scratch;
    }

    /**
     * 安全解析 XML（禁用外部 DTD/实体，避免解析阶段访问外网）
     */
    private static Document parseXml(String xml) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        setFeatureQuietly(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        setFeatureQuietly(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureQuietly(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        try
        {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        }
        catch (Exception ignore)
        {
            // 忽略不支持该属性的实现
        }
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * 序列化 DOM 元素（不含 xml 声明与 DOCTYPE）
     */
    private static String serialize(Element element) throws Exception
    {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try
        {
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        }
        catch (Exception ignore)
        {
            // 部分实现不支持该属性，忽略
        }
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(element), new StreamResult(writer));
        return writer.toString();
    }

    private static void setFeatureQuietly(DocumentBuilderFactory factory, String feature, boolean value)
    {
        try
        {
            factory.setFeature(feature, value);
        }
        catch (Exception ignore)
        {
            // 忽略不支持该特性的实现
        }
    }

    private static String rootMessage(Throwable throwable)
    {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current)
        {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (StringUtils.isBlank(message))
        {
            message = current.getClass().getSimpleName();
        }
        log.debug("校验异常", throwable);
        return message;
    }
}
