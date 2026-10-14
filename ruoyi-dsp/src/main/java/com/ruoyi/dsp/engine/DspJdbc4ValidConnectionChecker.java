package com.ruoyi.dsp.engine;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.druid.pool.ValidConnectionChecker;

/**
 * 通用连接存活校验器：基于 JDBC4 标准接口 {@link Connection#isValid(int)}，不依赖方言相关的校验语句。
 * <p>
 * 平台允许填写任意 driver（含未预置的国产库），无法替使用者猜出 {@code SELECT 1} 还是
 * {@code SELECT 1 FROM DUAL}，因此这里用 JDBC4 标准接口探活，使连接池得以开启
 * 「借出前探活 / 空闲探活」，从而消除「长时间无请求后首次请求报陈旧连接失败」的问题。
 * <p>
 * 驱动未实现 {@code isValid} 时退化为执行显式配置的 {@code validationQuery}；两者都没有则视为有效
 * （与未开启校验的历史行为一致）并打印一次告警。
 * <p>
 * 注意：本方法由连接池在借出现场调用，<b>绝不能向外抛异常</b>——否则会中断借出流程，把「连接失效」
 * 变成「接口报错」。因此所有分支都收敛为布尔值。
 *
 * @author ruoyi
 */
public class DspJdbc4ValidConnectionChecker implements ValidConnectionChecker
{
    private static final Logger log = LoggerFactory.getLogger(DspJdbc4ValidConnectionChecker.class);

    /** JDBC4 不支持告警只打印一次，避免刷日志 */
    private volatile boolean jdbc4UnsupportedWarned = false;

    @Override
    public boolean isValidConnection(Connection connection, String validationQuery, int validationQueryTimeout)
    {
        if (connection == null)
        {
            return false;
        }
        try
        {
            if (connection.isClosed())
            {
                return false;
            }
            // JDBC4 语义：timeout 单位为秒，0 表示不限制
            return connection.isValid(Math.max(validationQueryTimeout, 0));
        }
        catch (SQLFeatureNotSupportedException | UnsupportedOperationException | AbstractMethodError e)
        {
            return fallbackByValidationQuery(connection, validationQuery);
        }
        catch (Throwable e)
        {
            // 探活本身抛异常（连接已被对端断开、通道失效等）即判定为无效，交由连接池丢弃重建
            log.debug("连接探活异常，判定为无效连接", e);
            return false;
        }
    }

    @Override
    public void configFromProperties(Properties properties)
    {
        // 无额外配置项
    }

    /**
     * 驱动不支持 JDBC4 探测时，退化到显式配置的校验语句
     */
    private boolean fallbackByValidationQuery(Connection connection, String validationQuery)
    {
        if (validationQuery != null && !validationQuery.isEmpty())
        {
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(validationQuery))
            {
                return true;
            }
            catch (Throwable e)
            {
                log.debug("校验语句执行失败，判定为无效连接", e);
                return false;
            }
        }
        if (!jdbc4UnsupportedWarned)
        {
            jdbc4UnsupportedWarned = true;
            log.warn("当前驱动不支持 JDBC4 Connection.isValid 且未配置校验语句，该数据源无法探活，"
                    + "长时间空闲后可能出现首次请求失败：{}", connection.getClass().getName());
        }
        return true;
    }
}
