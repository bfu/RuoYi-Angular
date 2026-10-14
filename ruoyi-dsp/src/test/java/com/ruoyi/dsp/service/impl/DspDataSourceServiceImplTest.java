package com.ruoyi.dsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ruoyi.dsp.domain.DspDataSource;
import com.ruoyi.dsp.mapper.DspDataSourceMapper;
import com.ruoyi.dsp.mapper.DspScriptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link DspDataSourceServiceImpl} 单元测试
 * <p>
 * 重点覆盖：页面回显用的详情接口不回传数据库口令，而运行时取连接配置的查询仍带口令。
 *
 * @author ruoyi
 */
class DspDataSourceServiceImplTest
{
    private static final Long DATA_SOURCE_ID = 5L;

    private DspDataSourceMapper dspDataSourceMapper;

    private DspDataSourceServiceImpl service;

    @BeforeEach
    void setUp()
    {
        dspDataSourceMapper = mock(DspDataSourceMapper.class);
        service = new DspDataSourceServiceImpl();
        ReflectionTestUtils.setField(service, "dspDataSourceMapper", dspDataSourceMapper);
        ReflectionTestUtils.setField(service, "dspScriptMapper", mock(DspScriptMapper.class));
    }

    @Test
    void detailDoesNotReturnPassword()
    {
        when(dspDataSourceMapper.selectDspDataSourceById(DATA_SOURCE_ID)).thenReturn(configured());

        DspDataSource detail = service.selectDspDataSourceDetail(DATA_SOURCE_ID);

        assertNull(detail.getPassword());
        // 其余字段照常返回，供页面回显
        assertEquals("报表库", detail.getName());
        assertEquals("report", detail.getUsername());
        assertEquals("jdbc:mysql://127.0.0.1:3306/report", detail.getUrl());
    }

    @Test
    void detailOfMissingDataSourceReturnsNull()
    {
        when(dspDataSourceMapper.selectDspDataSourceById(404L)).thenReturn(null);

        assertNull(service.selectDspDataSourceDetail(404L));
    }

    @Test
    void runtimeQueryKeepsPasswordForConnectionPool()
    {
        when(dspDataSourceMapper.selectDspDataSourceById(DATA_SOURCE_ID)).thenReturn(configured());

        assertEquals("secret", service.selectDspDataSourceById(DATA_SOURCE_ID).getPassword());
    }

    private DspDataSource configured()
    {
        DspDataSource source = new DspDataSource();
        source.setDataSourceId(DATA_SOURCE_ID);
        source.setName("报表库");
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl("jdbc:mysql://127.0.0.1:3306/report");
        source.setUsername("report");
        source.setPassword("secret");
        return source;
    }
}
