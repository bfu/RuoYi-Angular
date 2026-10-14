package com.ruoyi.dsp.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link DspReloadMessage} 单元测试
 *
 * @author ruoyi
 */
class DspReloadMessageTest
{
    @Test
    void encodeAndDecodeRoundTrip()
    {
        DspReloadMessage source = new DspReloadMessage("8f3a", DspReloadScope.SCRIPT, List.of(12L, 15L));

        DspReloadMessage parsed = DspReloadMessage.decode(source.encode());

        assertEquals("8f3a", parsed.getInstanceId());
        assertEquals(DspReloadScope.SCRIPT, parsed.getScope());
        assertEquals(List.of(12L, 15L), parsed.getDataSourceIds());
    }

    @Test
    void encodeWithoutDataSourceIds()
    {
        DspReloadMessage parsed = DspReloadMessage.decode(new DspReloadMessage("a", DspReloadScope.ALL, null).encode());

        assertEquals(DspReloadScope.ALL, parsed.getScope());
        assertTrue(parsed.getDataSourceIds().isEmpty());
    }

    @Test
    void duplicateDataSourceIdsAreDeduplicated()
    {
        // 换绑等场景新旧数据源可能相同，重复重载没有意义
        DspReloadMessage message = new DspReloadMessage("a", DspReloadScope.SCRIPT, List.of(9L, 9L, 5L));

        assertEquals(List.of(9L, 5L), message.getDataSourceIds());
    }

    @Test
    void decodeRejectsIllegalPayload()
    {
        assertNull(DspReloadMessage.decode(null));
        assertNull(DspReloadMessage.decode("   "));
        assertNull(DspReloadMessage.decode("a|SCRIPT"));
        assertNull(DspReloadMessage.decode("a|UNKNOWN|1"));
        assertNull(DspReloadMessage.decode("a|SCRIPT|x"));
        assertNull(DspReloadMessage.decode("|SCRIPT|1"));
    }
}
