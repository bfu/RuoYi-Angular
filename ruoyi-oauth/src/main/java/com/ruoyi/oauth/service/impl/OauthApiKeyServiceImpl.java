package com.ruoyi.oauth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.enums.UserStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.oauth.domain.OauthApiKey;
import com.ruoyi.oauth.mapper.OauthApiKeyMapper;
import com.ruoyi.oauth.service.IOauthApiKeyService;
import com.ruoyi.system.service.ISysUserService;

/**
 * API Key 授权 服务层实现
 *
 * @author ruoyi
 */
@Service
public class OauthApiKeyServiceImpl implements IOauthApiKeyService
{
    private static final Logger log = LoggerFactory.getLogger(OauthApiKeyServiceImpl.class);

    /** 状态：正常 */
    private static final String STATUS_OK = "0";

    /** 明文密钥前缀 */
    private static final String KEY_PREFIX = "rak_";

    /** 最近使用时间更新节流间隔（毫秒），避免每次请求都写库 */
    private static final long LAST_USED_THROTTLE_MILLIS = 60L * 1000L;

    @Autowired
    private OauthApiKeyMapper oauthApiKeyMapper;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private SysPermissionService permissionService;

    @Override
    public List<OauthApiKey> selectOauthApiKeyList(OauthApiKey oauthApiKey)
    {
        return oauthApiKeyMapper.selectOauthApiKeyList(oauthApiKey);
    }

    @Override
    public OauthApiKey selectOauthApiKeyById(Long keyId)
    {
        return oauthApiKeyMapper.selectOauthApiKeyById(keyId);
    }

    @Override
    public String insertOauthApiKey(OauthApiKey oauthApiKey)
    {
        String apiKey = generateApiKey();
        if (StringUtils.isBlank(oauthApiKey.getStatus()))
        {
            oauthApiKey.setStatus(STATUS_OK);
        }
        oauthApiKey.setKeyHash(sha256Hex(apiKey));
        oauthApiKey.setCreateBy(SecurityUtils.getUsername());
        oauthApiKeyMapper.insertOauthApiKey(oauthApiKey);
        return apiKey;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateOauthApiKey(OauthApiKey oauthApiKey)
    {
        OauthApiKey exist = oauthApiKeyMapper.selectOauthApiKeyById(oauthApiKey.getKeyId());
        if (exist == null)
        {
            throw new ServiceException("密钥不存在，修改失败");
        }
        oauthApiKey.setUpdateBy(SecurityUtils.getUsername());
        return oauthApiKeyMapper.updateOauthApiKey(oauthApiKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetOauthApiKey(Long keyId)
    {
        OauthApiKey exist = oauthApiKeyMapper.selectOauthApiKeyById(keyId);
        if (exist == null)
        {
            throw new ServiceException("密钥不存在，重置失败");
        }
        String apiKey = generateApiKey();
        oauthApiKeyMapper.resetOauthApiKey(keyId, sha256Hex(apiKey), SecurityUtils.getUsername());
        return apiKey;
    }

    @Override
    public int deleteOauthApiKeyByIds(Long[] keyIds)
    {
        return oauthApiKeyMapper.deleteOauthApiKeyByIds(keyIds);
    }

    @Override
    public LoginUser authenticate(String apiKey)
    {
        if (StringUtils.isBlank(apiKey))
        {
            return null;
        }
        OauthApiKey key = oauthApiKeyMapper.selectOauthApiKeyByKeyHash(sha256Hex(apiKey.trim()));
        if (key == null)
        {
            log.debug("API Key 认证失败：密钥不存在");
            return null;
        }
        if (!STATUS_OK.equals(key.getStatus()))
        {
            log.debug("API Key 认证失败：密钥[{}]已停用", key.getName());
            return null;
        }
        if (key.getExpiresTime() != null && key.getExpiresTime().before(new Date()))
        {
            log.debug("API Key 认证失败：密钥[{}]已过期", key.getName());
            return null;
        }
        SysUser user = userService.selectUserById(key.getUserId());
        if (user == null)
        {
            log.debug("API Key 认证失败：绑定用户[{}]不存在", key.getUserId());
            return null;
        }
        if (UserStatus.DELETED.getCode().equals(user.getDelFlag()))
        {
            log.debug("API Key 认证失败：绑定用户[{}]已被删除", user.getUserName());
            return null;
        }
        if (UserStatus.DISABLE.getCode().equals(user.getStatus()))
        {
            log.debug("API Key 认证失败：绑定用户[{}]已被停用", user.getUserName());
            return null;
        }
        touchLastUsed(key);
        return new LoginUser(user.getUserId(), user.getDeptId(), user, permissionService.getMenuPermission(user));
    }

    /**
     * 生成明文密钥：{@value #KEY_PREFIX} + 32 位十六进制
     */
    private String generateApiKey()
    {
        return KEY_PREFIX + IdUtils.fastSimpleUUID();
    }

    /**
     * 计算 SHA-256 十六进制摘要（认证时按摘要精确匹配，无法反查明文）
     */
    private String sha256Hex(String text)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ServiceException("API Key 摘要计算失败");
        }
    }

    /**
     * 节流更新最近使用时间（默认 60 秒内不重复写库；失败只记日志，不影响认证）
     */
    private void touchLastUsed(OauthApiKey key)
    {
        try
        {
            Date now = new Date();
            if (key.getLastUsedTime() != null
                    && now.getTime() - key.getLastUsedTime().getTime() < LAST_USED_THROTTLE_MILLIS)
            {
                return;
            }
            oauthApiKeyMapper.updateLastUsedTime(key.getKeyId(), now);
        }
        catch (Exception e)
        {
            log.warn("更新 API Key[{}] 最近使用时间失败：{}", key.getKeyId(), e.getMessage());
        }
    }
}
