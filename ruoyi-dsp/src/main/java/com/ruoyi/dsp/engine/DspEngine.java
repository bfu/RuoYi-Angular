package com.ruoyi.dsp.engine;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.SimpleTypeRegistry;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.dsp.domain.DspDataSource;

/**
 * 动态脚本引擎
 * <p>
 * 负责将数据库中的 Mapper XML 在运行时解析并注册进目标数据源的 MyBatis
 * {@link Configuration}（以 namespace 隔离），并统一执行。
 * 语句按「数据源 + namespace（脚本 ID）」两级隔离：脚本绑定哪条数据源，就在哪条数据源的连接上执行。
 * 支持热加载（发布/回滚/下线/换绑）与事务回滚预览（测试）。
 *
 * @author ruoyi
 */
@Component
public class DspEngine
{
    private static final Logger log = LoggerFactory.getLogger(DspEngine.class);

    /** 数据源会话管理器（默认库 + 运行时数据源） */
    private final DspDataSourceManager dataSourceManager;

    /** 数据源 ID → (命名空间 → 语句数量) */
    private final Map<Long, Map<String, Integer>> loadedNamespaces = new ConcurrentHashMap<>();

    public DspEngine(DspDataSourceManager dataSourceManager)
    {
        this.dataSourceManager = dataSourceManager;
    }

    /**
     * 注册（或覆盖）指定数据源下某个 namespace 的脚本语句
     *
     * @param dataSourceId 数据源 ID（{@code 0} 或 {@code null} 表示默认主库）
     * @param namespace    命名空间（脚本 ID）
     * @param statementId  语句 id（脚本 slug）
     * @param content      语句片段（xml 声明、DOCTYPE、mapper 包装与 id 由平台统一生成）
     */
    public synchronized void register(Long dataSourceId, String namespace, String statementId, String content)
    {
        if (StringUtils.isBlank(namespace))
        {
            throw new ServiceException("namespace 不能为空");
        }
        if (StringUtils.isBlank(statementId))
        {
            throw new ServiceException("语句 id 不能为空");
        }
        if (StringUtils.isBlank(content))
        {
            throw new ServiceException("脚本内容为空，无法加载：" + namespace);
        }
        DspDataSourceManager.Holder holder = dataSourceManager.resolve(dataSourceId);
        // 先移除旧语句，避免 StrictMap 重复键异常
        unregister(dataSourceId, namespace);
        // 使用唯一资源名，绕过 XMLMapperBuilder 的 loadedResource 去重
        String resource = key(dataSourceId) + "-" + namespace + "-" + System.nanoTime() + ".xml";
        String xmlContent = DspValidator.mapperXml(namespace, statementId, content);
        try
        {
            XMLMapperBuilder builder = new XMLMapperBuilder(
                    new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)), holder.getConfiguration(),
                    resource, holder.getConfiguration().getSqlFragments());
            builder.parse();
        }
        catch (Exception e)
        {
            log.error("动态脚本加载失败 dataSourceId={}, namespace={}", dataSourceId, namespace, e);
            throw new ServiceException("动态脚本加载失败：" + rootMessage(e));
        }
        int count = countStatements(holder.getConfiguration(), namespace);
        if (count == 0)
        {
            unregister(dataSourceId, namespace);
            throw new ServiceException("脚本未解析出可执行语句，请检查 <select>/<insert>/<update>/<delete> 标签：" + namespace);
        }
        namespacesOf(dataSourceId).put(namespace, count);
        log.info("动态脚本已加载 dataSourceId={}, namespace={}, statementId={}, statements={}", dataSourceId,
                namespace, statementId, count);
    }

    /**
     * 卸载指定数据源下某个 namespace 的全部语句与 sql 片段
     */
    public synchronized void unregister(Long dataSourceId, String namespace)
    {
        Map<String, Integer> namespaces = namespacesOf(dataSourceId);
        if (StringUtils.isBlank(namespace) || !namespaces.containsKey(namespace))
        {
            return;
        }
        try
        {
            Configuration configuration = dataSourceManager.resolve(dataSourceId).getConfiguration();
            removeStatements(configuration, namespace);
        }
        catch (Exception e)
        {
            // 数据源已被销毁等情况：仅清理引擎记录（原 Configuration 随之废弃）
            log.debug("卸载语句时无法获取数据源配置 dataSourceId={}, namespace={}", dataSourceId, namespace, e);
        }
        namespaces.remove(namespace);
        log.info("动态脚本已卸载 dataSourceId={}, namespace={}", dataSourceId, namespace);
    }

    /**
     * 卸载指定数据源下的全部语句（数据源停用/删除/变更时调用）
     */
    public synchronized void unregisterAll(Long dataSourceId)
    {
        Map<String, Integer> namespaces = loadedNamespaces.get(dsKey(dataSourceId));
        if (namespaces == null || namespaces.isEmpty())
        {
            return;
        }
        for (String namespace : new ArrayList<>(namespaces.keySet()))
        {
            unregister(dataSourceId, namespace);
        }
        loadedNamespaces.remove(dsKey(dataSourceId));
    }

    /**
     * 是否已加载指定数据源下的命名空间
     */
    public boolean hasNamespace(Long dataSourceId, String namespace)
    {
        return StringUtils.isNotBlank(namespace) && namespacesOf(dataSourceId).containsKey(namespace);
    }

    /**
     * 已加载命名空间总数（含全部数据源）
     */
    public int loadedNamespaceCount()
    {
        int count = 0;
        for (Map<String, Integer> namespaces : loadedNamespaces.values())
        {
            count += namespaces.size();
        }
        return count;
    }

    /**
     * 指定数据源上是否还有已加载的语句（用于判断其运行时连接池可否回收）
     */
    public boolean hasAnyNamespace(Long dataSourceId)
    {
        Map<String, Integer> namespaces = loadedNamespaces.get(dsKey(dataSourceId));
        return namespaces != null && !namespaces.isEmpty();
    }

    /**
     * 执行脚本语句
     *
     * @param dataSourceId 数据源 ID（{@code 0}/{@code null} 为默认主库）
     * @param namespace    命名空间（脚本 ID）
     * @param statementId  语句 id（脚本 slug），或完整语句 id
     * @param params       参数
     * @param rollback     是否事务回滚预览
     * @return 执行结果
     */
    public DspExecutionResult execute(Long dataSourceId, String namespace, String statementId,
            Map<String, Object> params, boolean rollback)
    {
        if (StringUtils.isBlank(namespace) || StringUtils.isBlank(statementId))
        {
            throw new ServiceException("namespace 与 statementId 不能为空");
        }
        DspDataSourceManager.Holder holder = dataSourceManager.resolve(dataSourceId);
        Configuration configuration = holder.getConfiguration();
        String fullId = statementId.indexOf('.') >= 0 ? statementId : namespace + "." + statementId;
        if (!configuration.hasStatement(fullId))
        {
            throw new ServiceException("语句不存在：" + fullId);
        }
        MappedStatement mappedStatement = configuration.getMappedStatement(fullId);
        Object parameter = buildParameter(mappedStatement, params);
        if (rollback)
        {
            return executeWithRollback(holder, fullId, mappedStatement, parameter);
        }
        return doExecute(holder, fullId, mappedStatement, parameter, false);
    }

    /**
     * 事务回滚预览：在独立事务中执行后置为回滚，查询结果可见、DML 不落库
     * <p>
     * 使用目标数据源自己的事务管理器，保证跨库连接同样被回滚。
     */
    private DspExecutionResult executeWithRollback(DspDataSourceManager.Holder holder, String fullId,
            MappedStatement mappedStatement, Object parameter)
    {
        TransactionTemplate template = new TransactionTemplate(holder.getTransactionManager());
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        DspExecutionResult[] resultHolder = new DspExecutionResult[1];
        template.execute(status ->
        {
            resultHolder[0] = doExecute(holder, fullId, mappedStatement, parameter, true);
            status.setRollbackOnly();
            return null;
        });
        return resultHolder[0];
    }

    /**
     * 按语句类型分发执行
     */
    private DspExecutionResult doExecute(DspDataSourceManager.Holder holder, String fullId,
            MappedStatement mappedStatement, Object parameter, boolean rollback)
    {
        long start = System.currentTimeMillis();
        DspExecutionResult result = new DspExecutionResult();
        SqlCommandType commandType = mappedStatement.getSqlCommandType();
        result.setSqlCommandType(commandType.name());
        result.setRollback(rollback);
        SqlSessionTemplate sqlSession = holder.getSqlSession();
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

    private int countStatements(Configuration configuration, String namespace)
    {
        String prefix = namespace + ".";
        Map<String, Object> statements = getField(configuration, "mappedStatements");
        if (statements == null)
        {
            return 0;
        }
        int count = 0;
        for (String statementKey : statements.keySet())
        {
            if (statementKey.startsWith(prefix))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * 从 MyBatis 配置中移除指定 namespace 的语句与 sql 片段
     */
    private void removeStatements(Configuration configuration, String namespace)
    {
        String prefix = namespace + ".";
        Map<String, Object> statements = getField(configuration, "mappedStatements");
        if (statements != null)
        {
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, Object> entry : statements.entrySet())
            {
                String statementKey = entry.getKey();
                if (statementKey.startsWith(prefix))
                {
                    keys.add(statementKey);
                }
                else if (statementKey.indexOf('.') < 0 && entry.getValue() instanceof MappedStatement value
                        && value.getId() != null && value.getId().startsWith(prefix))
                {
                    // 清理指向本命名空间的短名映射
                    keys.add(statementKey);
                }
            }
            for (String statementKey : keys)
            {
                statements.remove(statementKey);
            }
        }
        Map<String, Object> fragments = getField(configuration, "sqlFragments");
        if (fragments != null)
        {
            List<String> keys = new ArrayList<>();
            for (String fragmentKey : fragments.keySet())
            {
                if (fragmentKey.startsWith(prefix))
                {
                    keys.add(fragmentKey);
                }
            }
            for (String fragmentKey : keys)
            {
                fragments.remove(fragmentKey);
            }
        }
    }

    private Map<String, Integer> namespacesOf(Long dataSourceId)
    {
        return loadedNamespaces.computeIfAbsent(dsKey(dataSourceId), key -> new ConcurrentHashMap<>());
    }

    private Long dsKey(Long dataSourceId)
    {
        return DspDataSourceManager.isDefault(dataSourceId) ? DspDataSource.DEFAULT_ID : dataSourceId;
    }

    private String key(Long dataSourceId)
    {
        return String.valueOf(dsKey(dataSourceId));
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
