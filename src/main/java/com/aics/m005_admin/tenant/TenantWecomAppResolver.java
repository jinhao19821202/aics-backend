package com.aics.m005_admin.tenant;

import com.aics.common.BizException;
import com.aics.common.crypto.LlmSecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 根据 tenantCode 解析租户及其企微应用配置；企微回调解密必须拿到 tenant 级别的 token/aesKey/secret。
 */
@Service
@RequiredArgsConstructor
public class TenantWecomAppResolver {

    private final TenantRepository tenantRepo;
    private final TenantWecomAppRepository wecomRepo;
    private final LlmSecretCipher cipher;

    public Resolved resolveByTenantCode(String tenantCode) {
        Tenant t = tenantRepo.findByCode(tenantCode)
                .orElseThrow(() -> new BizException(404, "unknown tenant code: " + tenantCode));
        if (!"active".equals(t.getStatus())) {
            throw new BizException(403, "tenant disabled: " + tenantCode);
        }
        List<TenantWecomApp> apps = wecomRepo.findByTenantIdAndEnabledTrue(t.getId());
        if (apps.isEmpty()) {
            throw new BizException(404, "no enabled wecom app for tenant " + tenantCode);
        }
        TenantWecomApp app = apps.get(0);
        String token = app.getToken();
        String aesKey = cipher.decrypt(app.getAesKeyCipher());
        String secret = cipher.decrypt(app.getSecretCipher());
        return new Resolved(t.getId(), app, token, aesKey, secret);
    }

    public record Resolved(Long tenantId, TenantWecomApp app, String token, String aesKey, String secret) {}
}
