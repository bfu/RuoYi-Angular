package com.ruoyi.dsp.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/**
 * {@link DspJdbc4ValidConnectionChecker} 单元测试
 * <p>
 * 关注点：探活结果必须真实反映连接可用性；驱动不支持 JDBC4 时按校验语句退化；
 * 且任何情况下都<b>不得抛出异常</b>（否则会把「连接失效」变成「接口报错」）。
 *
 * @author ruoyi
 */
class DspJdbc4ValidConnectionCheckerTest
{
    private static final int TIMEOUT_SECONDS = 3;

    private final DspJdbc4ValidConnectionChecker checker = new DspJdbc4ValidConnectionChecker();

    @Test
    void nullConnectionIsInvalid()
    {
        assertFalse(checker.isValidConnection(null, null, TIMEOUT_SECONDS));
    }

    @Test
    void liveConnectionIsValid() throws Exception
    {
        try (Connection connection = newH2Connection())
        {
            assertTrue(checker.isValidConnection(connection, null, TIMEOUT_SECONDS));
        }
    }

    @Test
    void closedConnectionIsInvalid() throws Exception
    {
        Connection connection = newH2Connection();
        connection.close();

        assertFalse(checker.isValidConnection(connection, null, TIMEOUT_SECONDS));
    }

    @Test
    void isValidThrowingSqlExceptionMeansInvalid() throws Exception
    {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenAnswer(invocation ->
        {
            throw new SQLException("connection reset by peer", "08006");
        });

        // 对端已断开时驱动会抛异常，此处必须收敛为 false 交给连接池剔除
        assertFalse(checker.isValidConnection(connection, null, TIMEOUT_SECONDS));
    }

    @Test
    void runtimeExceptionFromDriverIsSwallowed() throws Exception
    {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenThrow(new IllegalStateException("driver bug"));

        assertFalse(checker.isValidConnection(connection, null, TIMEOUT_SECONDS));
    }

    @Test
    void fallsBackToValidationQueryWhenJdbc4Unsupported() throws Exception
    {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenThrow(new SQLFeatureNotSupportedException());
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("select 1")).thenReturn(resultSet);

        assertTrue(checker.isValidConnection(connection, "select 1", TIMEOUT_SECONDS));
        verify(statement).executeQuery("select 1");
    }

    @Test
    void fallbackQueryFailureMeansInvalid() throws Exception
    {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenThrow(new SQLFeatureNotSupportedException());
        when(connection.createStatement()).thenThrow(new SQLException("connection is closed"));

        assertFalse(checker.isValidConnection(connection, "select 1", TIMEOUT_SECONDS));
    }

    @Test
    void assumesValidWhenNeitherJdbc4NorQueryAvailable() throws Exception
    {
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenThrow(new SQLFeatureNotSupportedException());

        // 老驱动且未配置校验语句：无法探活，保持与未开启校验一致，但不得报错
        assertTrue(checker.isValidConnection(connection, null, TIMEOUT_SECONDS));
    }

    @Test
    void negativeTimeoutIsClampedToZero() throws Exception
    {
        Connection connection = mock(Connection.class);
        when(connection.isValid(0)).thenReturn(true);

        // Druid 默认 validationQueryTimeout 为 -1，不能直接透传给 isValid
        assertTrue(checker.isValidConnection(connection, null, -1));
    }

    private Connection newH2Connection() throws SQLException
    {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:checker_" + System.nanoTime());
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource.getConnection();
    }
}
