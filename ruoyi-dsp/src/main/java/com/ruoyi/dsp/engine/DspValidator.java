package com.ruoyi.dsp.engine;

import java.io.StringReader;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
 * 脚本内容仅需编写语句片段（{@code <select>/<insert>/<update>/<delete>} 等，语句 id 只写语句名），
 * XML 声明、DOCTYPE 与 {@code <mapper>} 包装由平台统一生成，并以脚本 slug 作为 namespace，
 * 即运行时完整语句 id 为 {@code slug.语句名}（由平台拼接）。
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
     * 将语句片段包装为合法的 Mapper XML
     *
     * @param namespace 运行时 namespace（脚本 slug）
     * @param content   语句片段（可含历史包装，自动剥离）
     */
    public static String wrap(String namespace, String content)
    {
        return MAPPER_PROLOG + "<mapper namespace=\"" + namespace + "\">\n" + unwrap(content) + "\n</mapper>";
    }

    /**
     * 校验脚本内容
     *
     * @param slug    脚本标识（作为运行时 namespace，与语句名拼接为完整语句 id）
     * @param content 语句片段
     * @return 校验结果
     */
    public DspValidationResult validate(String slug, String content)
    {
        DspValidationResult result = new DspValidationResult();
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

        String namespace = slug;
        result.setNamespace(namespace);
        if (hasWrapper(content))
        {
            result.addWarning("已自动忽略 <?xml>、DOCTYPE 与 <mapper> 包装，仅需编写 <select>/<insert>/<update>/<delete> 语句");
        }

        String body = unwrap(content);
        if (StringUtils.isBlank(body))
        {
            result.addError("未找到任何 <select>/<insert>/<update>/<delete> 语句");
            return result;
        }

        String xml = wrap(namespace, body);
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
        collectStatements(root, result);
        if (result.getStatements().isEmpty())
        {
            result.addError("未找到任何 <select>/<insert>/<update>/<delete> 语句");
        }

        if (content.contains("${"))
        {
            result.addWarning("检测到 ${} 原始拼接，存在 SQL 注入风险，请确认参数来源可信");
        }

        // 基于临时 Configuration 做 MyBatis 语义校验（复用主配置类型别名）
        try
        {
            Configuration scratch = newScratchConfiguration();
            XMLMapperBuilder builder = new XMLMapperBuilder(new StringReader(xml), scratch,
                    namespace + "-validate.xml", scratch.getSqlFragments());
            builder.parse();
        }
        catch (Exception e)
        {
            result.addError("MyBatis 解析失败：" + rootMessage(e));
        }
        return result;
    }

    /**
     * 收集语句 id，并做基础完整性校验
     */
    private void collectStatements(Element root, DspValidationResult result)
    {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE)
            {
                continue;
            }
            String nodeName = node.getNodeName();
            if (!isStatementNode(nodeName))
            {
                continue;
            }
            Element element = (Element) node;
            String id = element.getAttribute("id");
            if (StringUtils.isBlank(id))
            {
                result.addError("<" + nodeName + "> 缺少 id 属性");
                continue;
            }
            if (id.indexOf('.') >= 0)
            {
                // 完整语句 id 为 slug.语句名，由平台拼接，内容中只需写语句名
                result.addError("<" + nodeName + "> 的 id 不能包含 '.'：请只写语句名（slug 前缀由平台拼接）");
                continue;
            }
            result.getStatements().add(id);
            if ("select".equalsIgnoreCase(nodeName)
                    && StringUtils.isBlank(element.getAttribute("resultType"))
                    && StringUtils.isBlank(element.getAttribute("resultMap")))
            {
                result.addError("<select id=\"" + id + "\"> 必须指定 resultType 或 resultMap");
            }
        }
    }

    private boolean isStatementNode(String nodeName)
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
     * 安全解析 XML（禁用外部 DTD/实体，避免校验阶段访问外网）
     */
    private Document parseXml(String xml) throws Exception
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

    private void setFeatureQuietly(DocumentBuilderFactory factory, String feature, boolean value)
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

    private String rootMessage(Throwable throwable)
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
