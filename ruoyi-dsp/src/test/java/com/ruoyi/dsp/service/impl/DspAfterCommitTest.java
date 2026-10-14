package com.ruoyi.dsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link DspAfterCommit} 单元测试
 *
 * @author ruoyi
 */
class DspAfterCommitTest
{
    @Test
    void runsImmediatelyWhenNoTransaction()
    {
        AtomicBoolean ran = new AtomicBoolean(false);

        DspAfterCommit.run(() -> ran.set(true));

        assertTrue(ran.get());
    }

    @Test
    void defersUntilCommitWhenTransactionActive()
    {
        TransactionSynchronizationManager.initSynchronization();
        try
        {
            AtomicBoolean ran = new AtomicBoolean(false);

            DspAfterCommit.run(() -> ran.set(true));

            // 事务尚未提交，内存变更不得执行
            assertFalse(ran.get());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            assertTrue(ran.get());
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
