package com.ruoyi.dsp.engine;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
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

    /** 翻页子句起始关键字：LIMIT（MySQL/PostgreSQL）或 OFFSET ... FETCH NEXT（Oracle/SQL Server） */
    private static final Pattern LIMIT_KEYWORD = Pattern.compile("\\blimit\\b");

    private static final Pattern FETCH_KEYWORD = Pattern.compile("\\bfetch\\b");

    private static final Pattern OFFSET_KEYWORD = Pattern.compile("\\boffset\\b");

    /** 翻页子句内允许出现的关键字，其余内容视为子句已结束 */
    private static final Pattern PAGE_CLAUSE_KEYWORD = Pattern
            .compile("(?i)\\b(limit|offset|rows|fetch|next|only)\\b");

    /** 可无损转为整数的字符串（LIMIT/OFFSET 只接受整数，字符串会被渲染成字面量导致语法错误） */
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[+-]?\\d+");

    /** 页大小参数名（生成器固定用 size，手工脚本常见 pageSize），用于限制单次返回行数 */
    private static final Pattern PAGE_SIZE_PROPERTY = Pattern.compile("(?i).*size.*");

    /** 单次返回行数上限，避免一次把整表拉进内存 */
    private static final long MAX_PAGE_SIZE = 10000L;

    /**
     * 随脚本一起增删的 MyBatis 注册表：语句、resultMap、parameterMap 与 sql 片段。
     * 注册键统一以「脚本 ID.」开头，重发布时必须连旧的一并摘掉，否则 StrictMap 会报重复键。
     */
    private static final String[] NAMESPACE_REGISTRIES = { "mappedStatements", "resultMaps", "parameterMaps",
            "sqlFragments" };

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
     * <p>
     * 覆盖采用「摘旧注册留底 → 解析 → 失败原样回放」：解析或校验失败时旧语句会被放回，
     * 一次失败的重新发布不会再让正在运行的已发布版本凭空消失。整个过程持有该数据源的写锁，
     * 期间执行侧拿不到读锁，也就读不到「语句暂时不存在」的中间状态。
     *
     * @param dataSourceId 数据源 ID（{@code 0} 或 {@code null} 表示默认主库）
     * @param namespace    命名空间（脚本 ID）
     * @param statementId  语句 id（脚本 slug）
     * @param content      语句片段（xml 声明、DOCTYPE、mapper 包装与 id 由平台统一生成）
     */
    public void register(Long dataSourceId, String namespace, String statementId, String content)
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
        String xmlContent = DspValidator.mapperXml(namespace, statementId, content);
        Lock lock = holder.writeLock();
        lock.lock();
        try
        {
            Configuration configuration = holder.getConfiguration();
            Map<String, Map<String, Object>> removed = detachNamespace(configuration, namespace);
            try
            {
                // 使用唯一资源名，绕过 XMLMapperBuilder 的 loadedResource 去重
                String resource = key(dataSourceId) + "-" + namespace + "-" + System.nanoTime() + ".xml";
                XMLMapperBuilder builder = new XMLMapperBuilder(
                        new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)), configuration, resource,
                        configuration.getSqlFragments());
                builder.parse();
                int count = countStatements(configuration, namespace);
                if (count == 0)
                {
                    throw new ServiceException(
                            "脚本未解析出可执行语句，请检查 <select>/<insert>/<update>/<delete> 标签：" + namespace);
                }
                namespacesOf(dataSourceId).put(namespace, count);
                log.info("动态脚本已加载 dataSourceId={}, namespace={}, statementId={}, statements={}", dataSourceId,
                        namespace, statementId, count);
            }
            catch (Exception e)
            {
                restoreNamespace(configuration, namespace, removed);
                if (e instanceof ServiceException serviceException)
                {
                    throw serviceException;
                }
                log.error("动态脚本加载失败 dataSourceId={}, namespace={}", dataSourceId, namespace, e);
                throw new ServiceException("动态脚本加载失败：" + rootMessage(e));
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * 卸载指定数据源下某个 namespace 的全部注册项（语句、resultMap、sql 片段）
     */
    public void unregister(Long dataSourceId, String namespace)
    {
        Map<String, Integer> namespaces = namespacesOf(dataSourceId);
        if (StringUtils.isBlank(namespace) || !namespaces.containsKey(namespace))
        {
            return;
        }
        try
        {
            DspDataSourceManager.Holder holder = dataSourceManager.resolve(dataSourceId);
            Lock lock = holder.writeLock();
            lock.lock();
            try
            {
                detachNamespace(holder.getConfiguration(), namespace);
            }
            finally
            {
                lock.unlock();
            }
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
    public void unregisterAll(Long dataSourceId)
    {
        Map<String, Integer> namespaces = loadedNamespaces.get(dsKey(dataSourceId));
        if (namespaces == null || namespaces.isEmpty())
        {
            return;
        }
        try
        {
            DspDataSourceManager.Holder holder = dataSourceManager.resolve(dataSourceId);
            Lock lock = holder.writeLock();
            lock.lock();
            try
            {
                Configuration configuration = holder.getConfiguration();
                for (String namespace : new ArrayList<>(namespaces.keySet()))
                {
                    detachNamespace(configuration, namespace);
                }
            }
            finally
            {
                lock.unlock();
            }
        }
        catch (Exception e)
        {
            log.debug("卸载全部语句时无法获取数据源配置 dataSourceId={}", dataSourceId, e);
        }
        namespaces.clear();
        loadedNamespaces.remove(dsKey(dataSourceId));
        log.info("数据源语句已全部卸载 dataSourceId={}", dataSourceId);
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
     * 已加载过脚本的数据源ID（含默认主库 {@code 0}），用于整体重载前先清空运行时注册
     */
    public Set<Long> loadedDataSourceIds()
    {
        return new HashSet<>(loadedNamespaces.keySet());
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
        // 读锁：与注册/卸载互斥，避免执行期间撞上半更新的语句注册表（拿一次语句超时或读不到）
        Lock lock = holder.readLock();
        lock.lock();
        try
        {
            Configuration configuration = holder.getConfiguration();
            String fullId = statementId.indexOf('.') >= 0 ? statementId : namespace + "." + statementId;
            if (!configuration.hasStatement(fullId))
            {
                throw new ServiceException("语句不存在：" + fullId);
            }
            MappedStatement mappedStatement = configuration.getMappedStatement(fullId);
            Object parameter = buildParameter(mappedStatement, params);
            parameter = normalizePageParams(mappedStatement, parameter);
            if (rollback)
            {
                return executeWithRollback(holder, fullId, mappedStatement, parameter);
            }
            return doExecute(holder, fullId, mappedStatement, parameter, false);
        }
        finally
        {
            lock.unlock();
        }
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

    /**
     * 翻页参数规整：LIMIT / OFFSET 只接受整数，而 JSON 文本与 URL 查询串里的取值天然是字符串，
     * 客户端预编译（MySQL 默认 useServerPrepStmts=false）会把字符串渲染成字面量，
     * 拼出的 {@code ... limit '1', '1'} 在数据库端是语法错误。
     * <p>
     * 此处<b>仅</b>把翻页子句内占位符上的整数字符串转成 {@link Long}，其余参数（等值条件等）保持原样，
     * 避免把字符串列上的数字串参数误转成数值比较导致隐式转换与索引失效。
     *
     * @param mappedStatement 目标语句
     * @param parameter       绑定参数（Map 或简单类型标量）
     * @return 规整后的绑定参数
     */
    private Object normalizePageParams(MappedStatement mappedStatement, Object parameter)
    {
        if (parameter == null)
        {
            return null;
        }
        BoundSql boundSql;
        try
        {
            boundSql = mappedStatement.getBoundSql(parameter);
        }
        catch (Exception e)
        {
            // 动态标签求值失败等问题交由真正执行时报错，此处不兜底
            log.debug("翻页参数规整时无法构建 SQL：{}", mappedStatement.getId(), e);
            return parameter;
        }
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        if (mappings.isEmpty())
        {
            return parameter;
        }
        Set<String> pageProperties = pagePlaceholderProperties(boundSql.getSql(), mappings);
        if (pageProperties.isEmpty())
        {
            return parameter;
        }
        if (parameter instanceof Map)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) parameter;
            for (String property : pageProperties)
            {
                if (values.containsKey(property))
                {
                    values.put(property, toPageNumber(property, values.get(property)));
                }
            }
            return parameter;
        }
        // 简单类型参数仅一处占位符：位于翻页子句时直接按类型转换
        if (pageProperties.contains(mappings.get(0).getProperty()))
        {
            return toPageNumber(mappings.get(0).getProperty(), parameter);
        }
        return parameter;
    }

    /**
     * 翻页占位符取值：整数字符串转 {@link Long}、整数类型直接取值，并校验非负与页大小上限，
     * 其余取值直接给出可读错误，避免数据库抛出难以定位的 SQL 语法错误
     */
    private Object toPageNumber(String property, Object value)
    {
        Long number = toIntegralNumber(property, value);
        if (number == null)
        {
            // 非数值取值（null、布尔等）原样透传，交由数据库报错
            return value;
        }
        if (number < 0)
        {
            throw new ServiceException("翻页参数不能为负数：" + property + " = " + value);
        }
        if (PAGE_SIZE_PROPERTY.matcher(property).matches() && number > MAX_PAGE_SIZE)
        {
            throw new ServiceException("翻页参数超出上限（最大 " + MAX_PAGE_SIZE + "）：" + property + " = " + value);
        }
        return number;
    }

    /**
     * 把翻页取值规整为整数：整数字符串按整数文本解析、整数类型直接取值，
     * 浮点类型与非法文本给出可读错误（LIMIT/OFFSET 只接受整数）
     *
     * @return 整数值；取值不是数值类型时返回 {@code null}
     */
    private Long toIntegralNumber(String property, Object value)
    {
        if (value instanceof String text)
        {
            String trimmed = text.trim();
            if (INTEGER_PATTERN.matcher(trimmed).matches())
            {
                try
                {
                    return Long.valueOf(trimmed);
                }
                catch (NumberFormatException e)
                {
                    throw new ServiceException("翻页参数超出范围：" + property + " = " + trimmed);
                }
            }
            throw new ServiceException("翻页参数（limit/offset）必须是整数：" + property + " = " + value);
        }
        if (value instanceof Number numberValue)
        {
            if (numberValue instanceof Double || numberValue instanceof Float || numberValue instanceof BigDecimal)
            {
                // JSON 里的 5.0 会解析成浮点，落到 LIMIT 同样是语法错误
                throw new ServiceException("翻页参数（limit/offset）必须是整数：" + property + " = " + value);
            }
            return numberValue.longValue();
        }
        return null;
    }

    /**
     * 找出翻页子句内占位符对应的参数名
     *
     * @param sql      已构建的 SQL（{@code #{}} 已替换为 ?）
     * @param mappings 占位符与参数的有序映射
     */
    private Set<String> pagePlaceholderProperties(String sql, List<ParameterMapping> mappings)
    {
        int clauseStart = pageClauseStart(sql);
        if (clauseStart < 0)
        {
            return Collections.emptySet();
        }
        int clauseEnd = pageClauseEnd(sql, clauseStart);
        Set<String> properties = new HashSet<>();
        int index = 0;
        for (int i = 0; i < sql.length(); i++)
        {
            char c = sql.charAt(i);
            if (c == '\'')
            {
                // 跳过字符串字面量，其中的 ? 不是占位符
                i = skipLiteral(sql, i);
            }
            else if (c == '?')
            {
                if (i >= clauseStart && i < clauseEnd && index < mappings.size())
                {
                    properties.add(mappings.get(index).getProperty());
                }
                index++;
            }
        }
        return properties;
    }

    /**
     * 翻页子句起始位置：取最后一处 limit，或 offset ... rows fetch next 中的 offset；
     * 未检测到翻页子句时返回 -1
     */
    private int pageClauseStart(String sql)
    {
        String lower = sql.toLowerCase(Locale.ROOT);
        int limit = lastMatch(LIMIT_KEYWORD, lower);
        if (limit >= 0)
        {
            return limit;
        }
        int fetch = lastMatch(FETCH_KEYWORD, lower);
        if (fetch < 0)
        {
            return -1;
        }
        int offset = lastBefore(OFFSET_KEYWORD, lower, fetch);
        return offset < 0 ? fetch : offset;
    }

    /**
     * 翻页子句结束位置（不含）：子句内只允许空白、逗号、占位符与翻页关键字，
     * 其它内容（union 后的语句、子查询右括号等）视为子句已结束
     */
    private int pageClauseEnd(String sql, int clauseStart)
    {
        int i = clauseStart;
        while (i < sql.length())
        {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c) || c == ',' || c == '?')
            {
                i++;
                continue;
            }
            Matcher keyword = PAGE_CLAUSE_KEYWORD.matcher(sql).region(i, sql.length());
            if (keyword.lookingAt())
            {
                i = keyword.end();
                continue;
            }
            break;
        }
        return i;
    }

    /** 返回字符串字面量结束位置下标（未闭合时返回语句末尾） */
    private int skipLiteral(String sql, int start)
    {
        for (int i = start + 1; i < sql.length(); i++)
        {
            if (sql.charAt(i) == '\'')
            {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'')
                {
                    // '' 为转义写法，继续向后找
                    i++;
                    continue;
                }
                return i;
            }
        }
        return sql.length() - 1;
    }

    private int lastMatch(Pattern pattern, String text)
    {
        Matcher matcher = pattern.matcher(text);
        int index = -1;
        while (matcher.find())
        {
            index = matcher.start();
        }
        return index;
    }

    private int lastBefore(Pattern pattern, String text, int end)
    {
        Matcher matcher = pattern.matcher(text);
        int index = -1;
        while (matcher.find() && matcher.start() < end)
        {
            index = matcher.start();
        }
        return index;
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
     * 从 MyBatis 配置中摘除指定 namespace 的全部注册项并留底
     *
     * @param configuration MyBatis 配置
     * @param namespace     命名空间（脚本 ID）
     * @return 注册表字段名 → (注册键 → 原值)；注册失败时用 {@link #restoreNamespace} 原样回放
     */
    private Map<String, Map<String, Object>> detachNamespace(Configuration configuration, String namespace)
    {
        String prefix = namespace + ".";
        Map<String, Map<String, Object>> removed = new LinkedHashMap<>();
        for (String registry : NAMESPACE_REGISTRIES)
        {
            Map<String, Object> entries = getField(configuration, registry);
            Map<String, Object> backup = new LinkedHashMap<>();
            if (entries != null)
            {
                Iterator<Map.Entry<String, Object>> iterator = entries.entrySet().iterator();
                while (iterator.hasNext())
                {
                    Map.Entry<String, Object> entry = iterator.next();
                    if (belongsToNamespace(entry.getKey(), entry.getValue(), prefix))
                    {
                        backup.put(entry.getKey(), entry.getValue());
                        iterator.remove();
                    }
                }
            }
            removed.put(registry, backup);
        }
        return removed;
    }

    /**
     * 回放一次失败注册之前摘除的注册项：先清掉本次解析可能已写入的半成品，再把旧值补回
     */
    private void restoreNamespace(Configuration configuration, String namespace,
            Map<String, Map<String, Object>> removed)
    {
        detachNamespace(configuration, namespace);
        for (Map.Entry<String, Map<String, Object>> registry : removed.entrySet())
        {
            Map<String, Object> entries = getField(configuration, registry.getKey());
            if (entries == null)
            {
                continue;
            }
            for (Map.Entry<String, Object> entry : registry.getValue().entrySet())
            {
                // 短名映射会随完整 id 一并补回，此处只补缺失的键，避免触发 StrictMap 重复键异常
                if (!entries.containsKey(entry.getKey()))
                {
                    entries.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * 注册键是否属于该命名空间：完整 id 以「脚本 ID.」开头，或为指向本命名空间的短名映射
     */
    private boolean belongsToNamespace(String key, Object value, String prefix)
    {
        if (key.startsWith(prefix))
        {
            return true;
        }
        return key.indexOf('.') < 0 && value instanceof MappedStatement statement && statement.getId() != null
                && statement.getId().startsWith(prefix);
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
