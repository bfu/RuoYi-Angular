package com.ruoyi.oauth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.UserStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.oauth.domain.OauthApiKey;
import com.ruoyi.oauth.mapper.OauthApiKeyMapper;
import com.ruoyi.oauth.service.IOauthApiKeyService;
import com.ruoyi.system.service.ISysUserService;

/**
 * {@link OauthApiKeyServiceImpl} 单元测试
 * <p>
 * 覆盖：密钥生成与摘要存储、重置换新摘要、认证全链路判定
 * （不存在/停用/过期/绑定用户不可用/正常）、最近使用时间节流。
 *
 * @author ruoyi
 */
class OauthApiKeyServiceImplTest
{
    private OauthApiKeyMapper oauthApiKeyMapper;

    private ISysUserService userService;

    private SysPermissionService permissionService;

    private IOauthApiKeyService service;

    @BeforeEach
    void setUp()
    {
        oauthApiKeyMapper = mock(OauthApiKeyMapper.class);
        userService = mock(ISysUserService.class);
        permissionService = mock(SysPermissionService.class);
        service = new OauthApiKeyServiceImpl();
        ReflectionTestUtils.setField(service, "oauthApiKeyMapper", oauthApiKeyMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "permissionService", permissionService);
        SysUser current = new SysUser();
        current.setUserId(1L);
        current.setUserName("admin");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new LoginUser(current.getUserId(), 100L, current, Set.of()), null));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void insertShouldGenerateKeyAndStoreHashOnly()
    {
        when(oauthApiKeyMapper.insertOauthApiKey(any(OauthApiKey.class))).thenReturn(1);

        OauthApiKey key = new OauthApiKey();
        key.setName("第三方系统");
        key.setUserId(2L);
        String apiKey = service.insertOauthApiKey(key);

        assertTrue(apiKey.startsWith("rak_"));
        assertEquals(36, apiKey.length());
        ArgumentCaptor<OauthApiKey> captor = ArgumentCaptor.forClass(OauthApiKey.class);
        verify(oauthApiKeyMapper).insertOauthApiKey(captor.capture());
        assertEquals(sha256Hex(apiKey), captor.getValue().getKeyHash());
        assertEquals("0", captor.getValue().getStatus());
    }

    @Test
    void resetShouldReplaceHash()
    {
        OauthApiKey exist = new OauthApiKey();
        exist.setKeyId(1L);
        exist.setName("第三方系统");
        when(oauthApiKeyMapper.selectOauthApiKeyById(1L)).thenReturn(exist);

        String apiKey = service.resetOauthApiKey(1L);

        assertTrue(apiKey.startsWith("rak_"));
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(oauthApiKeyMapper).resetOauthApiKey(eq(1L), hashCaptor.capture(), anyString());
        assertEquals(sha256Hex(apiKey), hashCaptor.getValue());
    }

    @Test
    void resetShouldFailWhenKeyMissing()
    {
        when(oauthApiKeyMapper.selectOauthApiKeyById(1L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.resetOauthApiKey(1L));
    }

    @Test
    void updateShouldFailWhenKeyMissing()
    {
        when(oauthApiKeyMapper.selectOauthApiKeyById(1L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.updateOauthApiKey(new OauthApiKey()));
    }

    @Test
    void authenticateShouldReturnLoginUserForValidKey()
    {
        OauthApiKey key = validKey();
        when(oauthApiKeyMapper.selectOauthApiKeyByKeyHash(anyString())).thenReturn(key);
        when(userService.selectUserById(2L)).thenReturn(activeUser());
        when(permissionService.getMenuPermission(any(SysUser.class))).thenReturn(Set.of("dsp:execute"));

        LoginUser loginUser = service.authenticate(key.getApiKey());

        assertNotNull(loginUser);
        assertEquals(2L, loginUser.getUserId());
        assertEquals(100L, loginUser.getDeptId());
        assertEquals(Set.of("dsp:execute"), loginUser.getPermissions());
        assertEquals("zhangsan", loginUser.getUsername());
        // 首次使用（lastUsedTime 为空）应写最近使用时间
        verify(oauthApiKeyMapper, times(1)).updateLastUsedTime(eq(1L), any(Date.class));
    }

    @Test
    void authenticateShouldThrottleLastUsedUpdate()
    {
        OauthApiKey key = validKey();
        key.setLastUsedTime(new Date(System.currentTimeMillis() - 10_000L));
        when(oauthApiKeyMapper.selectOauthApiKeyByKeyHash(anyString())).thenReturn(key);
        when(userService.selectUserById(2L)).thenReturn(activeUser());
        when(permissionService.getMenuPermission(any(SysUser.class))).thenReturn(Set.of());

        assertNotNull(service.authenticate(key.getApiKey()));
        verify(oauthApiKeyMapper, never()).updateLastUsedTime(anyLong(), any(Date.class));
    }

    @Test
    void authenticateShouldRejectUnknownKey()
    {
        when(oauthApiKeyMapper.selectOauthApiKeyByKeyHash(anyString())).thenReturn(null);
        assertNull(service.authenticate("rak_unknown"));
    }

    @Test
    void authenticateShouldRejectBlankKey()
    {
        assertNull(service.authenticate("  "));
        verify(oauthApiKeyMapper, never()).selectOauthApiKeyByKeyHash(anyString());
    }

    @Test
    void authenticateShouldRejectDisabledKey()
    {
        OauthApiKey key = validKey();
        key.setStatus("1");
        when(oauthApiKeyMapper.selectOauthApiKeyByKeyHash(anyString())).thenReturn(key);
        assertNull(service.authenticate(key.getApiKey()));
        verify(userService, never()).selectUserById(anyLong());
    }

    @Test
    void authenticateShouldRejectExpiredKey()
    {
        OauthApiKey key = validKey();
        key.setExpiresTime(new Date(System.currentTimeMillis() - 60_000L));
        when(oauthApiKeyMapper.selectOauthApiKeyByKeyHash(anyString())).thenReturn(key);
        assertNull(service.authenticate(key.getApiKey()));
        verify(userService, never()).selectUserById(anyLong());
    }

    @Test
    void authenticateShouldRejectDeletedOrDisabledUser()
    {
        OauthApiKey key = validKey();
        when(oauthApiKeyMapper.selectOauthApiKeyByKeyHash(anyString())).thenReturn(key);
        SysUser deleted = activeUser();
        deleted.setDelFlag(UserStatus.DELETED.getCode());
        when(userService.selectUserById(2L)).thenReturn(deleted);
        assertNull(service.authenticate(key.getApiKey()));

        SysUser disabled = activeUser();
        disabled.setStatus(UserStatus.DISABLE.getCode());
        when(userService.selectUserById(2L)).thenReturn(disabled);
        assertNull(service.authenticate(key.getApiKey()));
    }

    @Test
    void authenticateShouldRejectMissingUser()
    {
        OauthApiKey key = validKey();
        when(oauthApiKeyMapper.selectOauthApiKeyByKeyHash(anyString())).thenReturn(key);
        when(userService.selectUserById(2L)).thenReturn(null);
        assertNull(service.authenticate(key.getApiKey()));
    }

    private OauthApiKey validKey()
    {
        OauthApiKey key = new OauthApiKey();
        key.setKeyId(1L);
        key.setName("第三方系统");
        key.setApiKey("rak_0123456789abcdef0123456789abcdef");
        key.setUserId(2L);
        key.setStatus("0");
        return key;
    }

    private SysUser activeUser()
    {
        SysUser user = new SysUser();
        user.setUserId(2L);
        user.setDeptId(100L);
        user.setUserName("zhangsan");
        user.setDelFlag(UserStatus.OK.getCode());
        user.setStatus(UserStatus.OK.getCode());
        return user;
    }

    private String sha256Hex(String text)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }
}
