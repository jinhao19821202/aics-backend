package com.aics.m005_admin.llm;

import com.aics.common.BizException;
import com.aics.common.crypto.LlmSecretCipher;
import com.aics.config.AppProperties;
import com.aics.infra.dashscope.DashScopeClient;
import com.aics.infra.dashscope.DashScopeCreds;
import com.aics.infra.rerank.RerankClient;
import com.aics.m005_admin.tenant.TenantLlmConfig;
import com.aics.m005_admin.tenant.TenantLlmConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按租户解析 LLM 凭证并代理调用 DashScope。
 * 缓存：tenantId + "|" + purpose → DashScopeCreds；配置变更时由 LlmConfigService.evict() 清除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmClientResolver {

    private final TenantLlmConfigRepository repo;
    private final LlmSecretCipher cipher;
    private final DashScopeClient dashScope;
    private final AppProperties props;
    private final RerankClient rerankClient;

    private final ConcurrentHashMap<String, DashScopeCreds> credsCache = new ConcurrentHashMap<>();

    public DashScopeClient.ChatResult chat(Long tenantId, String model, String systemPrompt, String userPrompt,
                                           double temperature, int maxTokens) {
        DashScopeCreds creds = resolveOrFallback(tenantId, TenantLlmConfig.PURPOSE_CHAT);
        return dashScope.chat(creds, model, systemPrompt, userPrompt, temperature, maxTokens);
    }

    public List<float[]> embed(Long tenantId, List<String> texts) {
        DashScopeCreds creds = resolveOrFallback(tenantId, TenantLlmConfig.PURPOSE_EMBEDDING);
        return dashScope.embed(creds, texts);
    }

    public float[] embedOne(Long tenantId, String text) {
        DashScopeCreds creds = resolveOrFallback(tenantId, TenantLlmConfig.PURPOSE_EMBEDDING);
        return dashScope.embedOne(creds, text);
    }

    public DashScopeCreds resolveCreds(Long tenantId, String purpose) {
        return resolveOrFallback(tenantId, purpose);
    }

    public Integer resolveEmbeddingDim(Long tenantId) {
        DashScopeCreds creds = resolveOrFallback(tenantId, TenantLlmConfig.PURPOSE_EMBEDDING);
        return creds.embeddingDim() != null ? creds.embeddingDim() : props.getDashscope().getEmbeddingDim();
    }

    /**
     * 对候选集做精排。返回原顺序对应的 score 列表；未配置 rerank 返回 Optional.empty()。
     * 失败（包括超时）抛 RerankClient.RerankException，由调用方回退到粗排结果。
     */
    public Optional<RerankOutcome> rerank(Long tenantId, String query, List<String> documents, int topN) {
        if (tenantId == null || documents == null || documents.isEmpty()) return Optional.empty();
        TenantLlmConfig cfg = repo.findByTenantIdAndPurposeAndIsDefaultTrueAndEnabledTrue(
                tenantId, TenantLlmConfig.PURPOSE_RERANK).orElse(null);
        if (cfg == null) return Optional.empty();

        String apiKey;
        try {
            apiKey = cipher.decrypt(cfg.getApiKeyCipher());
        } catch (Exception e) {
            log.warn("rerank config decrypt failed tenant={}, skip rerank", tenantId);
            return Optional.empty();
        }

        RerankClient.Request req = new RerankClient.Request();
        req.provider = cfg.getProvider();
        req.model = cfg.getModel();
        req.baseUrl = cfg.getBaseUrl();
        req.apiKey = apiKey;
        req.query = query;
        req.documents = documents;
        req.topN = Math.max(1, topN);
        req.timeoutMs = resolveTimeoutMs(cfg.getParams());

        List<Double> scores = rerankClient.rerank(req);
        RerankOutcome o = new RerankOutcome();
        o.model = cfg.getModel();
        o.provider = cfg.getProvider();
        o.scores = scores;
        o.scoreFloor = resolveScoreFloor(cfg.getParams());
        o.topN = resolveTopN(cfg.getParams(), topN);
        return Optional.of(o);
    }

    private int resolveTimeoutMs(Map<String, Object> params) {
        if (params == null) return 3000;
        Object v = params.get("timeoutMs");
        if (v instanceof Number n) return Math.max(500, Math.min(30_000, n.intValue()));
        return 3000;
    }

    private Double resolveScoreFloor(Map<String, Object> params) {
        if (params == null) return null;
        Object v = params.get("scoreFloor");
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }

    private int resolveTopN(Map<String, Object> params, int requested) {
        if (params == null) return requested;
        Object v = params.get("topN");
        if (v instanceof Number n) return Math.max(1, Math.min(requested, n.intValue()));
        return requested;
    }

    public static class RerankOutcome {
        public String provider;
        public String model;
        public List<Double> scores;
        public Double scoreFloor;
        public int topN;
    }

    public void evict(Long tenantId) {
        if (tenantId == null) return;
        credsCache.keySet().removeIf(k -> k.startsWith(tenantId + "|"));
    }

    public void evictAll() {
        credsCache.clear();
    }

    private DashScopeCreds resolveOrFallback(Long tenantId, String purpose) {
        if (tenantId == null) return defaultCreds();
        String key = tenantId + "|" + purpose;
        return credsCache.computeIfAbsent(key, k -> loadFromDb(tenantId, purpose));
    }

    private DashScopeCreds loadFromDb(Long tenantId, String purpose) {
        return repo.findByTenantIdAndPurposeAndIsDefaultTrueAndEnabledTrue(tenantId, purpose)
                .map(c -> {
                    String key;
                    try {
                        key = cipher.decrypt(c.getApiKeyCipher());
                    } catch (Exception e) {
                        log.warn("decrypt llm apiKey failed tenant={} purpose={}, fallback to global", tenantId, purpose);
                        return defaultCreds();
                    }
                    String embedModel = TenantLlmConfig.PURPOSE_EMBEDDING.equals(purpose)
                            ? c.getModel() : props.getDashscope().getEmbeddingModel();
                    String chatModel = TenantLlmConfig.PURPOSE_CHAT.equals(purpose)
                            ? c.getModel() : props.getDashscope().getChatModel();
                    return new DashScopeCreds(key, c.getBaseUrl(), embedModel, chatModel, c.getEmbeddingDim());
                })
                .orElseGet(() -> {
                    log.debug("no active llm config for tenant={} purpose={}, using global default", tenantId, purpose);
                    return defaultCreds();
                });
    }

    private DashScopeCreds defaultCreds() {
        AppProperties.DashScope d = props.getDashscope();
        if (d == null || d.getApiKey() == null || d.getApiKey().isBlank()) {
            throw new BizException(500, "no global dashscope api key and no tenant override");
        }
        return new DashScopeCreds(d.getApiKey(), d.getBaseUrl(), d.getEmbeddingModel(), d.getChatModel(), d.getEmbeddingDim());
    }
}
