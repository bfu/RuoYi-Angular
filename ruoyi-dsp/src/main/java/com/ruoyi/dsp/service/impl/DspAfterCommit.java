package com.ruoyi.dsp.service.impl;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后回调
 * <p>
 * 动态脚本的运行时注册状态只存在于 JVM 内存（数据库里只有脚本定义与版本），而发布、下线、删除、
 * 换绑数据源都会同时改动数据库与内存。内存变更必须等数据库事务真正提交后再执行，否则事务回滚会留下
 * 「库里还是已发布、内存里已经卸掉」（脚本静默失效）或反向的不一致。
 *
 * @author ruoyi
 */
final class DspAfterCommit
{
    private DspAfterCommit()
    {
    }

    /**
     * 在当前事务提交后执行，无事务时立即执行
     *
     * @param action 内存变更动作
     */
    static void run(Runnable action)
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
