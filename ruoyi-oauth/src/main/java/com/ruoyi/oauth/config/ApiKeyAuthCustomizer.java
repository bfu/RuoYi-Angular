package com.ruoyi.oauth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.ruoyi.oauth.filter.ApiKeyAuthenticationFilter;

/**
 * API Key 免登录认证接入
 * <p>
 * 不改动 ruoyi-framework 的 {@code SecurityConfig}：Spring Security 会把容器内所有
 * {@code Customizer<HttpSecurity>} Bean 应用到每一条安全过滤器链，
 * 这里借此把 {@link ApiKeyAuthenticationFilter} 插入到原有 JWT 过滤器之前。
 * 未携带密钥请求头的请求行为与之前完全一致（仍走 JWT 流程）；
 * 携带请求头时由本过滤器完成认证，JWT 过滤器检测到已认证会自动跳过。
 * <p>
 * {@code ruoyi.oauth.enabled=false} 时本配置不装配，密钥请求头被忽略。
 *
 * @author ruoyi
 */
@Configuration
@ConditionalOnProperty(prefix = "ruoyi.oauth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ApiKeyAuthCustomizer
{
    @Bean
    public Customizer<HttpSecurity> apiKeyAuthenticationCustomizer(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter)
    {
        return http -> http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
