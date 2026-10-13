package com.ruoyi.dsp.engine;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
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
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 动态脚本校验器
 * <p>
 * 负责：XML 合法性、根节点与 namespace 约定、语句声明完整性，以及基于临时
 * {@link Configuration} 的 MyBatis 语义校验；校验过程不污染运行中的主配置。
 *
 * @author ruoyi
 */
@Component
public class DspValidator
{
    private static final Logger log = LoggerFactory.getLogger(DspValidator.class);

    /** 运行中的 MyBatis 配置，用于复用类型别名等元信息 */
    private final Configuration liveConfiguration;

    public DspValidator(SqlSessionFactory sqlSessionFactory)
    {
        this.liveConfiguration = sqlSessionFactory.getConfiguration();
    }

    /**
     * 校验脚本内容
     *
     * @param slug 脚本标识（应等于 XML 的 namespace）
     * @param xml  脚本内容
     * @return 校验结果
     */
    public DspValidationResult validate(String slug, String xml)
    {
        DspValidationResult result = new DspValidationResult();
        if (StringUtils.isBlank(xml))
        {
            result.addError("XML 内容为空");
            return result;
        }

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
        if (root == null || !"mapper".equalsIgnoreCase(root.getNodeName()))
        {
            result.addError("根节点必须为 <mapper>");
            return result;
        }

        String namespace = root.getAttribute("namespace");
        if (StringUtils.isBlank(namespace))
        {
            result.addError("<mapper> 缺少 namespace 属性");
            return result;
        }
        result.setNamespace(namespace);
        if (!namespace.equals(slug))
        {
            result.addError("namespace 必须与脚本标识一致（当前 namespace=\"" + namespace + "\"，slug=\"" + slug + "\"）");
            return result;
        }

        collectStatements(root, result);
        if (result.getStatements().isEmpty())
        {
            result.addError("未找到任何 <select>/<insert>/<update>/<delete> 语句");
        }

        if (xml.contains("${"))
        {
            result.addWarning("检测到 ${} 原始拼接，存在 SQL 注入风险，请确认参数来源可信");
        }

        // 基于临时 Configuration 做 MyBatis 语义校验（复用主配置类型别名）
        try
        {
            Configuration scratch = newScratchConfiguration();
            XMLMapperBuilder builder = new XMLMapperBuilder(new StringReader(xml), scratch,
                    slug + "-validate.xml", scratch.getSqlFragments());
            builder.parse();
        }
        catch (Exception e)
        {
            result.addError("MyBatis 解析失败：" + rootMessage(e));
        }
        return result;
    }

    /**
     * 将 XML 根节点的 namespace 重写为指定值，用于测试隔离（临时命名空间）
     */
    public String normalizeNamespace(String xml, String namespace)
    {
        try
        {
            Document document = parseXml(xml);
            Element root = document.getDocumentElement();
            if (root == null)
            {
                throw new ServiceException("XML 缺少根节点");
            }
            root.setAttribute("namespace", namespace);
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
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("重写 namespace 失败：" + rootMessage(e));
        }
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
