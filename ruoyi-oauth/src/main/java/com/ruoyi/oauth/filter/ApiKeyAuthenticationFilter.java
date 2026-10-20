package com.ruoyi.oauth.filter;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.oauth.config.OauthProperties;
import com.ruoyi.oauth.service.IOauthApiKeyService;

/**
 * API Key 免登录认证过滤器
 * <p>
 * 请求头携带有效密钥时直接构造绑定用户的身份上下文（等价于该用户已登录），
 * 无需 JWT；未携带请求头时直接放行，交由原有 JWT 过滤器处理。
 * 密钥无效、已停用、已过期或绑定用户不可用时返回 401。
 *
 * @author ruoyi
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter
{
    @Autowired
    private OauthProperties oauthProperties;

    @Autowired
    private IOauthApiKeyService oauthApiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        String apiKey = request.getHeader(oauthProperties.getHeaderName());
        if (StringUtils.isNotBlank(apiKey))
        {
            // 请求可能同时携带 JWT，已认证则不覆盖
            if (SecurityUtils.getAuthentication() == null)
            {
                LoginUser loginUser = oauthApiKeyService.authenticate(apiKey);
                if (loginUser == null)
                {
                    ServletUtils.renderString(response, JSON.toJSONString(
                            AjaxResult.error(HttpStatus.UNAUTHORIZED, "API Key 无效、已停用或已过期")));
                    return;
                }
                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }
        chain.doFilter(request, response);
    }
}
