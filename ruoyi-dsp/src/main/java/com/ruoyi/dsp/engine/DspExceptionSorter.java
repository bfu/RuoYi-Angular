package com.ruoyi.dsp.engine;

import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;
import com.alibaba.druid.pool.ExceptionSorter;

/**
 * 通用异常分类器：判断 {@link SQLException} 是否表示「连接已不可用」，供连接池剔除坏连接。
 * <p>
 * 平台允许填写任意 driver，无法预置厂商专有的 {@code ExceptionSorter}（Druid 内置的只覆盖
 * MySQL/Oracle/PostgreSQL/SQLServer 等已知驱动）。若不分类，Druid 会把「连接已断开」当成普通
 * 业务异常，坏连接被归还池中并<b>反复借出、反复失败</b>。
 * <p>
 * 判定以 SQL 标准状态类为主，并补充常见厂商的「连接已断开/握手失败」错误码。
 *
 * @author ruoyi
 */
public class DspExceptionSorter implements ExceptionSorter
{
    /** SQL 标准状态类：08 连接异常、28 认证失效、57 运维中断（如管理员关闭实例） */
    private static final Set<String> FATAL_SQL_STATE_CLASSES = Set.of("08", "28", "57");

    /** 异常链遍历深度上限，防御自引用的 cause */
    private static final int MAX_CAUSE_DEPTH = 10;

    @Override
    public boolean isExceptionFatal(SQLException e)
    {
        Throwable cause = e;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++, cause = cause.getCause())
        {
            if (cause instanceof SQLException sqlException && isFatal(sqlException))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public void configFromProperties(Properties properties)
    {
        // 无额外配置项
    }

    private boolean isFatal(SQLException e)
    {
        String sqlState = e.getSQLState();
        if (sqlState != null && sqlState.length() >= 2
                && FATAL_SQL_STATE_CLASSES.contains(sqlState.substring(0, 2)))
        {
            return true;
        }
        return isVendorConnectionLost(e.getErrorCode());
    }

    /**
     * 常见厂商的「连接已断开」错误码
     */
    private boolean isVendorConnectionLost(int errorCode)
    {
        return switch (errorCode)
        {
            // MySQL / MariaDB：bad handshake / server has gone away / lost connection / 连接被对端关闭
            case 1043, 2006, 2013, 2055 -> true;
            // Oracle / DM：not connected / end-of-file on communication channel
            case 1012, 3113, 3114 -> true;
            // SQL Server 与通用 Windows socket：连接被重置 / 连接超时
            case 10054, 10060 -> true;
            default -> false;
        };
    }
}
