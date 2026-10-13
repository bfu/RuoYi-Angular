package com.ruoyi.dsp.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import com.ruoyi.dsp.service.IDspScriptService;

/**
 * 动态脚本平台启动加载器
 * <p>
 * 应用启动完成后，将数据库中的全部已发布脚本热加载进 MyBatis 配置。
 *
 * @author ruoyi
 */
@Component
public class DspScriptLoader implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(DspScriptLoader.class);

    private final IDspScriptService dspScriptService;

    public DspScriptLoader(IDspScriptService dspScriptService)
    {
        this.dspScriptService = dspScriptService;
    }

    @Override
    public void run(ApplicationArguments args)
    {
        try
        {
            dspScriptService.reloadAll();
        }
        catch (Exception e)
        {
            log.error("动态脚本平台启动加载失败", e);
        }
    }
}
