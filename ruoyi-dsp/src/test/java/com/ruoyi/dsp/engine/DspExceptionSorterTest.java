package com.ruoyi.dsp.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * {@link DspExceptionSorter} 单元测试
 * <p>
 * 关注点：「连接已不可用」需判为致命（连接池据此剔除坏连接），
 * 而语法错误、约束冲突等业务异常不能被误判，否则会误伤健康连接。
 *
 * @author ruoyi
 */
class DspExceptionSorterTest
{
    private final DspExceptionSorter sorter = new DspExceptionSorter();

    @Test
    void nullIsNotFatal()
    {
        assertFalse(sorter.isExceptionFatal(null));
    }

    @Test
    void sqlStateClass08IsFatal()
    {
        assertTrue(sorter.isExceptionFatal(new SQLException("connection failure", "08006")));
        assertTrue(sorter.isExceptionFatal(new SQLException("link failure", "08S01")));
    }

    @Test
    void sqlStateClass28And57AreFatal()
    {
        assertTrue(sorter.isExceptionFatal(new SQLException("auth expired", "28000")));
        assertTrue(sorter.isExceptionFatal(new SQLException("admin shutdown", "57P01")));
    }

    @Test
    void vendorConnectionLostCodesAreFatal()
    {
        // MySQL: server has gone away / lost connection / bad handshake
        assertTrue(sorter.isExceptionFatal(new SQLException("gone", "HY000", 2006)));
        assertTrue(sorter.isExceptionFatal(new SQLException("lost", "HY000", 2013)));
        // Oracle: not connected
        assertTrue(sorter.isExceptionFatal(new SQLException("not connected", null, 1012)));
        // SQL Server / socket: 连接被重置
        assertTrue(sorter.isExceptionFatal(new SQLException("reset", null, 10054)));
    }

    @Test
    void wrappedCauseIsInspected()
    {
        SQLException fatal = new SQLException("connection reset", "08006");
        SQLException wrapper = new SQLException("execute failed", "42000", 1064, fatal);

        assertTrue(sorter.isExceptionFatal(wrapper));
    }

    @Test
    void businessErrorsAreNotFatal()
    {
        // 语法错误、唯一键冲突、表不存在：连接本身是好的，不能剔除
        assertFalse(sorter.isExceptionFatal(new SQLException("syntax error", "42000", 1064)));
        assertFalse(sorter.isExceptionFatal(new SQLException("duplicate key", "23000", 1062)));
        assertFalse(sorter.isExceptionFatal(new SQLException("table not found", "42S02", 1146)));
        assertFalse(sorter.isExceptionFatal(new SQLException("no sql state")));
    }

    @Test
    void cyclicCauseChainTerminates()
    {
        // Java 禁止自引用 cause，但两个异常互相引用仍可构造，遍历需要深度上限
        SQLException first = new SQLException("first", "42000");
        SQLException second = new SQLException("second", "42000", first);
        first.initCause(second);

        assertFalse(sorter.isExceptionFatal(first));
    }
}
