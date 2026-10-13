package com.ruoyi.dsp.engine;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.SimpleTypeRegistry;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;

/**
 * 动态脚本引擎
 * <p>
 * 负责将数据库中的 Mapper XML 在运行时解析并注册进现有 MyBatis
 * {@link Configuration}（以 namespace 隔离），并统一执行。
 * 支持热加载（发布/回滚/下线）与事务回滚预览（测试）。
 *
 * @author ruoyi
 */
@Component
public class DspEngine
{
    private static final Logger log = LoggerFactory.getLogger(DspEngine.class);

    private final Configuration configuration;

    private final SqlSessionTemplate sqlSession;

    private final PlatformTransactionManager transactionManager;

    /** 已加载命名空间 → 语句数量 */
    private final Map<String, Integer> loadedNamespaces = new ConcurrentHashMap<>();

    public DspEngine(SqlSessionFactory sqlSessionFactory, PlatformTransactionManager transactionManager)
    {
        this.configuration = sqlSessionFactory.getConfiguration();
        this.sqlSession = new SqlSessionTemplate(sqlSessionFactory);
        this.transactionManager = transactionManager;
    }

    /**
     * 注册（或覆盖）指定 namespace 的脚本语句
     *
     * @param namespace  命名空间（即脚本 ID，由 {@link DspValidator#namespaceOf(Long)} 生成）
     * @param content    语句片段（XML 声明、DOCTYPE 与 mapper 包装由平台统一生成）
     */
    public synchronized void register(String namespace, String content)
    {
        if (StringUtils.isBlank(namespace))
        {
            throw new ServiceException("namespace 不能为空");
        }
        if (StringUtils.isBlank(content))
        {
            throw new ServiceException("脚本内容为空，无法加载：" + namespace);
        }
        // 先移除旧语句，避免 StrictMap 重复键异常
        unregister(namespace);
        // 使用唯一资源名，绕过 XMLMapperBuilder 的 loadedResource 去重
        String resource = namespace + "-" + System.nanoTime() + ".xml";
        String xmlContent = DspValidator.wrap(namespace, content);
        try
        {
            XMLMapperBuilder builder = new XMLMapperBuilder(new StringReader(xmlContent), configuration, resource,
                    configuration.getSqlFragments());
            builder.parse();
        }
        catch (Exception e)
        {
            log.error("动态脚本加载失败 namespace={}", namespace, e);
            throw new ServiceException("动态脚本加载失败：" + rootMessage(e));
        }
        int count = countStatements(namespace);
        if (count == 0)
        {
            unregister(namespace);
            throw new ServiceException("脚本未解析出可执行语句，请检查 <select>/<insert>/<update>/<delete> 标签：" + namespace);
        }
        loadedNamespaces.put(namespace, count);
        log.info("动态脚本已加载 namespace={}, statements={}", namespace, count);
    }

    /**
     * 卸载指定 namespace 的全部语句与 sql 片段
     */
    public synchronized void unregister(String namespace)
    {
        if (StringUtils.isBlank(namespace))
        {
            return;
        }
        String prefix = namespace + ".";
        Map<String, Object> statements = getField(configuration, "mappedStatements");
        if (statements != null)
        {
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, Object> entry : statements.entrySet())
            {
                String key = entry.getKey();
                if (key.startsWith(prefix))
                {
                    keys.add(key);
                }
                else if (key.indexOf('.') < 0 && entry.getValue() instanceof MappedStatement value
                        && value.getId() != null && value.getId().startsWith(prefix))
                {
                    // 清理指向本命名空间的短名映射
                    keys.add(key);
                }
            }
            for (String key : keys)
            {
                statements.remove(key);
            }
        }
        Map<String, Object> fragments = getField(configuration, "sqlFragments");
        if (fragments != null)
        {
            List<String> keys = new ArrayList<>();
            for (String key : fragments.keySet())
            {
                if (key.startsWith(prefix))
                {
                    keys.add(key);
                }
            }
            for (String key : keys)
            {
                fragments.remove(key);
            }
        }
        loadedNamespaces.remove(namespace);
        log.info("动态脚本已卸载 namespace={}", namespace);
    }

    /**
     * 是否已加载指定命名空间
     */
    public boolean hasNamespace(String namespace)
    {
        return StringUtils.isNotBlank(namespace) && loadedNamespaces.containsKey(namespace);
    }

    /**
     * 已加载命名空间数量
     */
    public int loadedNamespaceCount()
    {
        return loadedNamespaces.size();
    }

    /**
     * 执行脚本语句
     *
     * @param namespace   命名空间（脚本 ID）
     * @param statementId 语句 id
     * @param params      参数
     * @param rollback    是否事务回滚预览
     * @return 执行结果
     */
    public DspExecutionResult execute(String namespace, String statementId, Map<String, Object> params, boolean rollback)
    {
        if (StringUtils.isBlank(namespace) || StringUtils.isBlank(statementId))
        {
            throw new ServiceException("namespace 与 statementId 不能为空");
        }
        String fullId = statementId.indexOf('.') >= 0 ? statementId : namespace + "." + statementId;
        if (!configuration.hasStatement(fullId))
        {
            throw new ServiceException("语句不存在：" + fullId);
        }
        MappedStatement mappedStatement = configuration.getMappedStatement(fullId);
        Object parameter = buildParameter(mappedStatement, params);
        if (rollback)
        {
            return executeWithRollback(fullId, mappedStatement, parameter);
        }
        return doExecute(fullId, mappedStatement, parameter, false);
    }

    /**
     * 事务回滚预览：在独立事务中执行后置为回滚，查询结果可见、DML 不落库
     */
    private DspExecutionResult executeWithRollback(String fullId, MappedStatement mappedStatement, Object parameter)
    {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        DspExecutionResult[] holder = new DspExecutionResult[1];
        template.execute(status ->
        {
            holder[0] = doExecute(fullId, mappedStatement, parameter, true);
            status.setRollbackOnly();
            return null;
        });
        return holder[0];
    }

    /**
     * 按语句类型分发执行
     */
    private DspExecutionResult doExecute(String fullId, MappedStatement mappedStatement, Object parameter, boolean rollback)
    {
        long start = System.currentTimeMillis();
        DspExecutionResult result = new DspExecutionResult();
        SqlCommandType commandType = mappedStatement.getSqlCommandType();
        result.setSqlCommandType(commandType.name());
        result.setRollback(rollback);
        switch (commandType)
        {
            case SELECT:
                result.setRows(toRowMaps(sqlSession.selectList(fullId, parameter)));
                break;
            case INSERT:
                result.setAffectedRows(sqlSession.insert(fullId, parameter));
                break;
            case UPDATE:
                result.setAffectedRows(sqlSession.update(fullId, parameter));
                break;
            case DELETE:
                result.setAffectedRows(sqlSession.delete(fullId, parameter));
                break;
            default:
                throw new ServiceException("不支持的语句类型：" + commandType);
        }
        result.setCostMillis(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 简单类型参数（如 Long/String）自动解包，兼容 parameterType 为标量的语句
     */
    private Object buildParameter(MappedStatement mappedStatement, Map<String, Object> params)
    {
        if (params == null || params.isEmpty())
        {
            return params;
        }
        Class<?> parameterType = mappedStatement.getParameterMap().getType();
        if (params.size() == 1 && isSimpleType(parameterType))
        {
            return params.values().iterator().next();
        }
        return params;
    }

    private boolean isSimpleType(Class<?> type)
    {
        return type != null && (type.isPrimitive() || type.isEnum() || SimpleTypeRegistry.isSimpleType(type));
    }

    private List<Map<String, Object>> toRowMaps(List<Object> rows)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        if (rows == null)
        {
            return list;
        }
        for (Object row : rows)
        {
            if (row == null)
            {
                list.add(null);
            }
            else if (row instanceof Map)
            {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) row).entrySet())
                {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                list.add(map);
            }
            else
            {
                JSONObject jsonObject = JSON.parseObject(JSON.toJSONString(row));
                list.add(new LinkedHashMap<>(jsonObject));
            }
        }
        return list;
    }

    private int countStatements(String namespace)
    {
        String prefix = namespace + ".";
        Map<String, Object> statements = getField(configuration, "mappedStatements");
        if (statements == null)
        {
            return 0;
        }
        int count = 0;
        for (String key : statements.keySet())
        {
            if (key.startsWith(prefix))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * 反射读取 MyBatis Configuration 的内部注册表（无公开的移除接口）
     */
    @SuppressWarnings("unchecked")
    private <T> T getField(Object target, String fieldName)
    {
        Class<?> clazz = target.getClass();
        while (clazz != null)
        {
            try
            {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return (T) field.get(target);
            }
            catch (NoSuchFieldException e)
            {
                clazz = clazz.getSuperclass();
            }
            catch (IllegalAccessException e)
            {
                log.warn("读取 Configuration 字段失败：{}", fieldName, e);
                return null;
            }
        }
        return null;
    }

    private String rootMessage(Throwable throwable)
    {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current)
        {
            current = current.getCause();
        }
        return StringUtils.isBlank(current.getMessage()) ? current.getClass().getSimpleName() : current.getMessage();
    }
}
