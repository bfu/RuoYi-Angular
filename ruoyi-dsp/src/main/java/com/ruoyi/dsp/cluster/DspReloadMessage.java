package com.ruoyi.dsp.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import com.ruoyi.common.utils.StringUtils;

/**
 * 动态脚本运行时变更广播消息
 * <p>
 * 语句注册只存在于各实例的 JVM 内存，数据库里只有脚本定义与版本。某个实例改了脚本或数据源之后，
 * 必须通知其余实例按数据库重建本地注册，否则请求落到别的实例会报「脚本未发布或不可用」。
 * <p>
 * 编码格式：{@code 实例ID|范围|数据源ID列表}，例如 {@code 8f3a|SCRIPT|12,15}。
 *
 * @author ruoyi
 */
public class DspReloadMessage
{
    /** 字段分隔符 */
    private static final String SEPARATOR = "|";

    /** 数据源ID分隔符 */
    private static final String ID_SEPARATOR = ",";

    /** 载荷字段数：实例ID、范围、数据源ID列表 */
    private static final int FIELD_COUNT = 3;

    private final String instanceId;

    private final DspReloadScope scope;

    private final List<Long> dataSourceIds;

    public DspReloadMessage(String instanceId, DspReloadScope scope, Collection<Long> dataSourceIds)
    {
        this.instanceId = instanceId;
        this.scope = scope;
        // 去重：换绑等场景新旧数据源可能相同，避免对同一数据源重复重载
        this.dataSourceIds = dataSourceIds == null ? Collections.emptyList()
                : List.copyOf(new LinkedHashSet<>(dataSourceIds));
    }

    /**
     * 编码为广播载荷
     */
    public String encode()
    {
        StringBuilder buffer = new StringBuilder();
        buffer.append(instanceId).append(SEPARATOR).append(scope.name()).append(SEPARATOR);
        for (int i = 0; i < dataSourceIds.size(); i++)
        {
            if (i > 0)
            {
                buffer.append(ID_SEPARATOR);
            }
            buffer.append(dataSourceIds.get(i));
        }
        return buffer.toString();
    }

    /**
     * 解析广播载荷
     *
     * @param payload 载荷文本
     * @return 格式非法时返回 {@code null}
     */
    public static DspReloadMessage decode(String payload)
    {
        if (StringUtils.isBlank(payload))
        {
            return null;
        }
        String[] fields = payload.split(Pattern.quote(SEPARATOR), -1);
        if (fields.length != FIELD_COUNT || StringUtils.isBlank(fields[0]))
        {
            return null;
        }
        DspReloadScope scope = toScope(fields[1]);
        if (scope == null)
        {
            return null;
        }
        List<Long> dataSourceIds = new ArrayList<>();
        if (StringUtils.isNotBlank(fields[2]))
        {
            for (String id : fields[2].split(Pattern.quote(ID_SEPARATOR)))
            {
                try
                {
                    dataSourceIds.add(Long.valueOf(id.trim()));
                }
                catch (NumberFormatException e)
                {
                    return null;
                }
            }
        }
        return new DspReloadMessage(fields[0], scope, dataSourceIds);
    }

    private static DspReloadScope toScope(String name)
    {
        for (DspReloadScope scope : DspReloadScope.values())
        {
            if (scope.name().equalsIgnoreCase(name))
            {
                return scope;
            }
        }
        return null;
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public DspReloadScope getScope()
    {
        return scope;
    }

    public List<Long> getDataSourceIds()
    {
        return dataSourceIds;
    }

    @Override
    public String toString()
    {
        return encode();
    }
}
