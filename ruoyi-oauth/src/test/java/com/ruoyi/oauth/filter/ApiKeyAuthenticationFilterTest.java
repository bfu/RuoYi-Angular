package com.ruoyi.oauth.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.oauth.config.OauthProperties;
import com.ruoyi.oauth.service.IOauthApiKeyService;

/**
 * {@link ApiKeyAuthenticationFilter} 单元测试
 * <p>
 * 覆盖：无请求头放行（走原 JWT 流程）、有效密钥写入身份上下文、
 * 无效密钥返回 401 且不继续过滤器链、已认证时不覆盖。
 *
 * @author ruoyi
 */
class ApiKeyAuthenticationFilterTest
{
    private static final String HEADER_NAME = "X-API-Key";

    private static final String API_KEY = "rak_0123456789abcdef0123456789abcdef";

    private IOauthApiKeyService oauthApiKeyService;

    private ApiKeyAuthenticationFilter filter;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    private FilterChain filterChain;

    @BeforeEach
    void setUp()
    {
        oauthApiKeyService = mock(IOauthApiKeyService.class);
        filter = new ApiKeyAuthenticationFilter();
        OauthProperties properties = new OauthProperties();
        properties.setHeaderName(HEADER_NAME);
        ReflectionTestUtils.setField(filter, "oauthProperties", properties);
        ReflectionTestUtils.setField(filter, "oauthApiKeyService", oauthApiKeyService);
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassThroughWhenHeaderAbsent() throws Exception
    {
        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(oauthApiKeyService);
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldIgnoreBlankHeader() throws Exception
    {
        request.addHeader(HEADER_NAME, "   ");

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(oauthApiKeyService);
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldAuthenticateAndSetSecurityContext() throws Exception
    {
        request.addHeader(HEADER_NAME, API_KEY);
        LoginUser loginUser = newLoginUser(Set.of("dsp:execute"));
        when(oauthApiKeyService.authenticate(API_KEY)).thenReturn(loginUser);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(loginUser, authentication.getPrincipal());
        assertEquals(1, authentication.getAuthorities().size());
        assertEquals("admin", authentication.getName());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldReturnUnauthorizedWhenKeyInvalid() throws Exception
    {
        request.addHeader(HEADER_NAME, API_KEY);
        when(oauthApiKeyService.authenticate(API_KEY)).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        // 与框架 AuthenticationEntryPointImpl 一致：HTTP 200 + body 内 code=401
        String body = response.getContentAsString();
        assertTrue(body.contains("API Key 无效"), body);
        assertTrue(body.contains("\"code\":401"), body);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldNotOverwriteExistingAuthentication() throws Exception
    {
        request.addHeader(HEADER_NAME, API_KEY);
        LoginUser existing = newLoginUser(Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        existing, null, existing.getAuthorities()));

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(oauthApiKeyService);
        verify(filterChain).doFilter(request, response);
    }

    private LoginUser newLoginUser(Set<String> permissions)
    {
        SysUser user = new SysUser();
        user.setUserId(1L);
        user.setDeptId(100L);
        user.setUserName("admin");
        return new LoginUser(user.getUserId(), user.getDeptId(), user, permissions);
    }

    @Test
    void headerNameShouldBeConfigurable() throws Exception
    {
        OauthProperties properties = new OauthProperties();
        properties.setHeaderName("X-Custom-Key");
        ReflectionTestUtils.setField(filter, "oauthProperties", properties);
        request.addHeader("X-Custom-Key", API_KEY);
        when(oauthApiKeyService.authenticate(anyString())).thenReturn(newLoginUser(Set.of()));

        filter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
