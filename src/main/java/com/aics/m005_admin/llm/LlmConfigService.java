package com.aics.m005_admin.llm;

import com.aics.common.BizException;
import com.aics.common.crypto.LlmSecretCipher;
import com.aics.common.tenant.TenantContext;
import com.aics.infra.rerank.RerankClient;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.tenant.TenantLlmConfig;
import com.aics.m005_admin.tenant.TenantLlmConfigRepository;
import com.aics.m005_admin.user.AdminPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户 LLM 配置（tenant_llm_config）读写 + 连通性测试。
 * api_key 永不以明文出库；写时 AES-GCM 加密，读时只返回 tail4。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmConfigService {

    private static final List<String> PURPOSES = List.of(
            TenantLlmConfig.PURPOSE_CHAT,
            TenantLlmConfig.PURPOSE_EMBEDDING,
            TenantLlmConfig.PURPOSE_RERANK);

    private final TenantLlmConfigRepository repo;
    private final LlmSecretCipher cipher;
    private final AdminAuditLogger audit;
    private final WebClient.Builder webClientBuilder;
    private final LlmClientResolver resolver;
    private final RerankClient rerankClient;

    public List<LlmConfigDto.Response> list() {
        Long tenantId = TenantContext.require();
        return repo.findByTenantIdOrderByIdDesc(tenantId).stream()
                .map(LlmConfigDto.Response::of)
                .toList();
    }

    public LlmConfigDto.Response get(Long id) {
        Long tenantId = TenantContext.require();
        return repo.findByIdAndTenantId(id, tenantId)
                .map(LlmConfigDto.Response::of)
                .orElseThrow(() -> new BizException(404, "llm config not found"));
    }

    @Transactional
    public LlmConfigDto.Response create(LlmConfigDto.Request req, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        validate(req, true);

        TenantLlmConfig c = new TenantLlmConfig();
        c.setTenantId(tenantId);
        c.setProvider(nvl(req.getProvider(), "dashscope"));
        c.setPurpose(req.getPurpose());
        c.setApiKeyCipher(cipher.encrypt(req.getApiKey()));
        c.setApiKeyTail(cipher.tail(req.getApiKey()));
        c.setBaseUrl(blankToNull(req.getBaseUrl()));
        c.setModel(req.getModel());
        c.setEmbeddingDim(req.getEmbeddingDim());
        c.setParams(req.getParams() == null ? new HashMap<>() : req.getParams());
        c.setIsDefault(Boolean.TRUE.equals(req.getIsDefault()));
        c.setEnabled(req.getEnabled() == null ? true : req.getEnabled());
        c.setCreatedBy(op == null ? null : op.id());
        c.setUpdatedAt(OffsetDateTime.now());

        if (Boolean.TRUE.equals(c.getIsDefault())) {
            clearDefaultsForPurpose(tenantId, req.getPurpose(), null);
        }
        c = repo.save(c);
        resolver.evict(tenantId);
        audit.record(op, "LLM_CONFIG_CREATE", "tenant_llm_config", String.valueOf(c.getId()),
                null, Map.of("purpose", c.getPurpose(), "model", c.getModel()));
        return LlmConfigDto.Response.of(c);
    }

    @Transactional
    public LlmConfigDto.Response update(Long id, LlmConfigDto.Request req, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        validate(req, false);

        TenantLlmConfig c = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BizException(404, "llm config not found"));

        Map<String, Object> before = snapshot(c);

        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            c.setApiKeyCipher(cipher.encrypt(req.getApiKey()));
            c.setApiKeyTail(cipher.tail(req.getApiKey()));
        }
        if (req.getProvider() != null) c.setProvider(req.getProvider());
        if (req.getPurpose() != null) c.setPurpose(req.getPurpose());
        if (req.getBaseUrl() != null) c.setBaseUrl(blankToNull(req.getBaseUrl()));
        if (req.getModel() != null) c.setModel(req.getModel());
        if (req.getEmbeddingDim() != null) c.setEmbeddingDim(req.getEmbeddingDim());
        if (req.getParams() != null) c.setParams(req.getParams());
        if (req.getEnabled() != null) c.setEnabled(req.getEnabled());

        if (Boolean.TRUE.equals(req.getIsDefault()) && !Boolean.TRUE.equals(c.getIsDefault())) {
            clearDefaultsForPurpose(tenantId, c.getPurpose(), c.getId());
            c.setIsDefault(true);
        } else if (Boolean.FALSE.equals(req.getIsDefault()) && Boolean.TRUE.equals(c.getIsDefault())) {
            c.setIsDefault(false);
        }
        c.setUpdatedAt(OffsetDateTime.now());

        c = repo.save(c);
        resolver.evict(tenantId);
        audit.record(op, "LLM_CONFIG_UPDATE", "tenant_llm_config", String.valueOf(id), before, snapshot(c));
        return LlmConfigDto.Response.of(c);
    }

    @Transactional
    public void delete(Long id, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        TenantLlmConfig c = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BizException(404, "llm config not found"));
        repo.delete(c);
        resolver.evict(tenantId);
        audit.record(op, "LLM_CONFIG_DELETE", "tenant_llm_config", String.valueOf(id),
                snapshot(c), null);
    }

    @Transactional
    public LlmConfigDto.TestResult test(Long id, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        TenantLlmConfig c = repo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BizException(404, "llm config not found"));

        long started = System.currentTimeMillis();
        LlmConfigDto.TestResult tr = new LlmConfigDto.TestResult();
        String plainKey;
        try {
            plainKey = cipher.decrypt(c.getApiKeyCipher());
        } catch (Exception e) {
            tr.setOk(false);
            tr.setMessage("密文解密失败：该配置的 api_key 是用旧主密钥加密的，已无法恢复。请删除此条并用当前主密钥重新录入。");
            tr.setLatencyMs(System.currentTimeMillis() - started);
            c.setLastTestAt(OffsetDateTime.now());
            c.setLastTestOk(false);
            c.setLastTestMsg(trunc(tr.getMessage(), 400));
            c.setUpdatedAt(OffsetDateTime.now());
            repo.save(c);
            audit.record(op, "LLM_CONFIG_TEST", "tenant_llm_config", String.valueOf(id),
                    null, Map.of("ok", false, "reason", "decrypt_failed"));
            return tr;
        }
        try {
            if (TenantLlmConfig.PURPOSE_EMBEDDING.equals(c.getPurpose())) {
                probeEmbedding(c, plainKey);
            } else if (TenantLlmConfig.PURPOSE_RERANK.equals(c.getPurpose())) {
                probeRerank(c, plainKey);
            } else {
                probeChat(c, plainKey);
            }
            tr.setOk(true);
            tr.setMessage("OK");
        } catch (Exception e) {
            tr.setOk(false);
            tr.setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        tr.setLatencyMs(System.currentTimeMillis() - started);

        c.setLastTestAt(OffsetDateTime.now());
        c.setLastTestOk(tr.isOk());
        c.setLastTestMsg(trunc(tr.getMessage(), 400));
        c.setUpdatedAt(OffsetDateTime.now());
        repo.save(c);
        audit.record(op, "LLM_CONFIG_TEST", "tenant_llm_config", String.valueOf(id),
                null, Map.of("ok", tr.isOk(), "latencyMs", tr.getLatencyMs()));
        return tr;
    }

    private void probeChat(TenantLlmConfig c, String apiKey) {
        WebClient wc = webClient(c.getBaseUrl(), apiKey);
        Map<String, Object> body = Map.of(
                "model", c.getModel(),
                "input", Map.of("messages", List.of(
                        Map.of("role", "user", "content", "ping"))),
                "parameters", Map.of("max_tokens", 8, "result_format", "message"));
        JsonNode resp = wc.post()
                .uri("/services/aigc/text-generation/generation")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(15));
        if (resp == null || resp.get("output") == null) {
            throw new RuntimeException("empty response");
        }
    }

    private void probeRerank(TenantLlmConfig c, String apiKey) {
        RerankClient.Request req = new RerankClient.Request();
        req.provider = c.getProvider();
        req.model = c.getModel();
        req.baseUrl = c.getBaseUrl();
        req.apiKey = apiKey;
        req.query = "如何开通售后服务";
        req.documents = List.of(
                "售后服务可在会员中心-我的订单中申请，需要提供订单号和联系方式。",
                "今天天气不错，适合出去野餐。");
        req.topN = 2;
        req.timeoutMs = 10_000;
        List<Double> scores = rerankClient.rerank(req);
        if (scores == null || scores.isEmpty()) {
            throw new RuntimeException("empty rerank response");
        }
    }

    private void probeEmbedding(TenantLlmConfig c, String apiKey) {
        WebClient wc = webClient(c.getBaseUrl(), apiKey);
        Map<String, Object> body = Map.of(
                "model", c.getModel(),
                "input", Map.of("texts", List.of("test")),
                "parameters", Map.of("text_type", "query"));
        JsonNode resp = wc.post()
                .uri("/services/embeddings/text-embedding/text-embedding")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(10));
        JsonNode embs = resp == null ? null : resp.path("output").path("embeddings");
        if (embs == null || !embs.isArray() || embs.isEmpty()) {
            throw new RuntimeException("empty embedding");
        }
    }

    private WebClient webClient(String baseUrl, String apiKey) {
        String url = (baseUrl == null || baseUrl.isBlank())
                ? "https://dashscope.aliyuncs.com/api/v1" : baseUrl;
        return webClientBuilder.clone()
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private void clearDefaultsForPurpose(Long tenantId, String purpose, Long excludeId) {
        List<TenantLlmConfig> rows = repo.findByTenantIdAndPurpose(tenantId, purpose);
        for (TenantLlmConfig r : rows) {
            if (excludeId != null && excludeId.equals(r.getId())) continue;
            if (Boolean.TRUE.equals(r.getIsDefault())) {
                r.setIsDefault(false);
                r.setUpdatedAt(OffsetDateTime.now());
                repo.save(r);
            }
        }
    }

    private void validate(LlmConfigDto.Request req, boolean isCreate) {
        if (req == null) throw new BizException(400, "body required");
        if (isCreate) {
            if (req.getPurpose() == null || !PURPOSES.contains(req.getPurpose())) {
                throw new BizException(400, "purpose must be chat, embedding or rerank");
            }
            if (req.getApiKey() == null || req.getApiKey().isBlank()) {
                throw new BizException(400, "apiKey required");
            }
            if (req.getModel() == null || req.getModel().isBlank()) {
                throw new BizException(400, "model required");
            }
            if (TenantLlmConfig.PURPOSE_EMBEDDING.equals(req.getPurpose())
                    && (req.getEmbeddingDim() == null || req.getEmbeddingDim() <= 0)) {
                throw new BizException(400, "embeddingDim required for embedding purpose");
            }
            if (TenantLlmConfig.PURPOSE_RERANK.equals(req.getPurpose())
                    && (req.getBaseUrl() == null || req.getBaseUrl().isBlank())
                    && !"dashscope".equalsIgnoreCase(req.getProvider())) {
                throw new BizException(400, "baseUrl required for non-dashscope rerank provider");
            }
        }
    }

    private Map<String, Object> snapshot(TenantLlmConfig c) {
        Map<String, Object> m = new HashMap<>();
        m.put("purpose", c.getPurpose());
        m.put("model", c.getModel());
        m.put("baseUrl", c.getBaseUrl());
        m.put("embeddingDim", c.getEmbeddingDim());
        m.put("isDefault", c.getIsDefault());
        m.put("enabled", c.getEnabled());
        m.put("apiKeyTail", c.getApiKeyTail());
        return m;
    }

    private static String nvl(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
