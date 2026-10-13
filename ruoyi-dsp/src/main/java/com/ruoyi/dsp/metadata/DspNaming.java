package com.ruoyi.dsp.metadata;

import java.util.Locale;
import com.ruoyi.common.utils.StringUtils;

/**
 * 元数据命名转换工具
 *
 * @author ruoyi
 */
public final class DspNaming
{
    private DspNaming()
    {
    }

    /**
     * 下划线/中划线/空格命名转小驼峰（如 user_name → userName）
     * <p>
     * 全大写名称（如 SYS_USER）先整体转小写再转驼峰，避免产生 sYSuSER 之类的可读性问题；
     * 已是驼峰的名称（如 userName）保持原有大小写，不做二次变换。
     */
    public static String toCamelCase(String name)
    {
        if (StringUtils.isBlank(name))
        {
            return name;
        }
        String source = name.equals(name.toUpperCase(Locale.ROOT)) ? name.toLowerCase(Locale.ROOT) : name;
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < source.length(); i++)
        {
            char current = source.charAt(i);
            if (current == '_' || current == '-' || current == ' ')
            {
                upperNext = true;
                continue;
            }
            if (builder.length() == 0)
            {
                builder.append(Character.toLowerCase(current));
            }
            else
            {
                builder.append(upperNext ? Character.toUpperCase(current) : current);
            }
            upperNext = false;
        }
        return builder.toString();
    }
}
