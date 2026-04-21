package com.aics.m005_admin.tenant;

import com.aics.common.crypto.LlmSecretCipher;
import com.aics.common.tenant.TenantContext;
import com.aics.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 启动时把 V7 种下的占位行替换为来自 AppProperties 的真实配置：
 *  - tenant_wecom_app：corp_id / agent_id / token / aes_key_cipher / secret_cipher
 *  - tenant_llm_config：chat + embedding 的 model / api_key_cipher / base_url
 *
 * 幂等：只修改仍为 '__PLACEHOLDER__' 的行；真实值一旦被管理员改过就不会被覆盖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(10)
public class DefaultTenantBootstrap implements ApplicationRunner {

    private static final String PLACEHOLDER = "__PLACEHOLDER__";
    private static final Long DEFAULT_TENANT = TenantContext.DEFAULT_TENANT_ID;

    private final AppProperties props;
    private final LlmSecretCipher cipher;
    private final TenantWecomAppRepository wecomRepo;
    private final TenantLlmConfigRepository llmRepo;
    private final TenantRepository tenantRepo;
    private final CollectionProvisioner collectionProvisioner;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        syncWecom();
        syncLlm();
        provisionDefaultCollection();
    }

    private void provisionDefaultCollection() {
        tenantRepo.findById(DEFAULT_TENANT).ifPresent(t -> {
            try {
                collectionProvisioner.provision(t);
            } catch (Exception e) {
                log.warn("DefaultTenantBootstrap: 默认租户 milvus collection provision 失败(Milvus 未启动?): {}", e.getMessage());
            }
        });
    }

    private void syncWecom() {
        AppProperties.WeCom w = props.getWecom();
        if (w == null || isBlank(w.getCorpId()) || isBlank(w.getSecret())) {
            log.info("DefaultTenantBootstrap: app.wecom.* 未完整配置，跳过企微 seed 回填");
            return;
        }
        List<TenantWecomApp> rows = wecomRepo.findByTenantId(DEFAULT_TENANT);
        TenantWecomApp target = rows.stream()
                .filter(r -> PLACEHOLDER.equals(r.getCorpId()) || PLACEHOLDER.equals(r.getToken()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            boolean hasReal = rows.stream().anyMatch(r -> w.getCorpId().equals(r.getCorpId())
                    && (w.getAgentId() == null ? r.getAgentId() == 0 : w.getAgentId().equals(r.getAgentId())));
            if (hasReal) {
                log.info("DefaultTenantBootstrap: 企微配置已存在真实记录，跳过");
                return;
            }
            target = new TenantWecomApp();
            target.setTenantId(DEFAULT_TENANT);
            target.setName("默认应用");
        }
        if (isBlank(target.getName())) {
            target.setName("默认应用");
        }
        target.setCorpId(w.getCorpId());
        target.setAgentId(w.getAgentId() == null ? 0 : w.getAgentId());
        target.setToken(nullSafe(w.getToken()));
        target.setAesKeyCipher(cipher.encrypt(nullSafe(w.getAesKey())));
        target.setSecretCipher(cipher.encrypt(w.getSecret()));
        target.setBotUserid(w.getBotUserid());
        target.setApiBase(w.getApiBase());
        target.setEnabled(true);
        target.setUpdatedAt(OffsetDateTime.now());
        wecomRepo.save(target);
        log.info("DefaultTenantBootstrap: 企微默认租户配置已回填 corpId={} agentId={}", w.getCorpId(), target.getAgentId());
    }

    private void syncLlm() {
        AppProperties.DashScope d = props.getDashscope();
        if (d == null || isBlank(d.getApiKey())) {
            log.info("DefaultTenantBootstrap: app.dashscope.api-key 未配置，跳过 LLM seed 回填");
            return;
        }
        upsertLlm(TenantLlmConfig.PURPOSE_CHAT, d.getChatModel(), d, null);
        upsertLlm(TenantLlmConfig.PURPOSE_EMBEDDING, d.getEmbeddingModel(), d, d.getEmbeddingDim());
    }

    private void upsertLlm(String purpose, String model, AppProperties.DashScope d, Integer dim) {
        if (isBlank(model)) {
            log.warn("DefaultTenantBootstrap: purpose={} 的 model 未配置，跳过", purpose);
            return;
        }
        List<TenantLlmConfig> rows = llmRepo.findByTenantIdAndPurpose(DEFAULT_TENANT, purpose);
        TenantLlmConfig target = rows.stream()
                .filter(r -> PLACEHOLDER.equals(r.getApiKeyCipher()) || PLACEHOLDER.equals(r.getModel()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            if (rows.stream().anyMatch(r -> Boolean.TRUE.equals(r.getEnabled()) && Boolean.TRUE.equals(r.getIsDefault()))) {
                log.info("DefaultTenantBootstrap: purpose={} 已存在启用的默认配置，跳过", purpose);
                return;
            }
            target = new TenantLlmConfig();
            target.setTenantId(DEFAULT_TENANT);
            target.setProvider("dashscope");
            target.setPurpose(purpose);
        }
        target.setProvider("dashscope");
        target.setModel(model);
        target.setBaseUrl(d.getBaseUrl());
        target.setApiKeyCipher(cipher.encrypt(d.getApiKey()));
        target.setApiKeyTail(cipher.tail(d.getApiKey()));
        if (dim != null) target.setEmbeddingDim(dim);
        target.setIsDefault(true);
        target.setEnabled(true);
        target.setUpdatedAt(OffsetDateTime.now());
        llmRepo.save(target);
        log.info("DefaultTenantBootstrap: LLM 默认租户 purpose={} model={} 配置已回填", purpose, model);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
