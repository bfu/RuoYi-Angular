package com.ruoyi.dsp.cluster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

/**
 * {@link DspReloadListener} 单元测试
 *
 * @author ruoyi
 */
class DspReloadListenerTest
{
    private DspReloadNotifier notifier;

    private DspRuntimeRefresher refresher;

    private DspReloadListener listener;

    @BeforeEach
    void setUp()
    {
        notifier = mock(DspReloadNotifier.class);
        refresher = mock(DspRuntimeRefresher.class);
        listener = new DspReloadListener(notifier, refresher);
    }

    @Test
    void refreshesOnMessageFromOtherInstance()
    {
        when(notifier.isFromSelf(any())).thenReturn(false);

        listener.onMessage(message("8f3a|SCRIPT|9"), null);

        verify(refresher).refresh(any(DspReloadMessage.class));
    }

    @Test
    void skipsMessageFromSelf()
    {
        when(notifier.isFromSelf(any())).thenReturn(true);

        listener.onMessage(message("8f3a|SCRIPT|9"), null);

        // 本实例的变更已就地生效，重复重载没有意义
        verify(refresher, never()).refresh(any());
    }

    @Test
    void ignoresUnparsablePayload()
    {
        listener.onMessage(message("garbage"), null);

        verify(refresher, never()).refresh(any());
    }

    @Test
    void refreshFailureDoesNotBreakTheListener()
    {
        when(notifier.isFromSelf(any())).thenReturn(false);
        doThrow(new IllegalStateException("db down")).when(refresher).refresh(any());

        listener.onMessage(message("8f3a|ALL|"), null);

        verify(refresher).refresh(any());
    }

    private Message message(String body)
    {
        return new DefaultMessage("ruoyi:dsp:reload".getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
