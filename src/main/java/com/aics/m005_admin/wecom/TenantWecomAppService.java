package com.aics.m005_admin.wecom;

import com.aics.common.BizException;
import com.aics.common.crypto.LlmSecretCipher;
import com.aics.common.tenant.TenantContext;
import com.aics.config.AppProperties;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.tenant.Tenant;
import com.aics.m005_admin.tenant.TenantRepository;
import com.aics.m005_admin.tenant.TenantWecomApp;
import com.aics.m005_admin.tenant.TenantWecomAppRepository;
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
 * P004-A F001：租户企微应用自助管理。
 * aesKey / secret 明文仅在当次请求中存在；落盘前必经 {@link LlmSecretCipher} 加密；响应只回 tail 占位符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantWecomAppService {

    private final TenantWecomAppRepository repo;
    private final TenantRepository tenantRepo;
    private final LlmSecretCipher cipher;
    private final AdminAuditLogger audit;
    private final AppProperties props;
    private final WebClient.Builder webClientBuilder;

    public List<TenantWecomAppDto.Response> list() {
        Long tenantId = TenantContext.require();
        String tenantCode = tenantCode(tenantId);
        return repo.findByTenantIdOrderByIdAsc(tenantId).stream()
                .map(a -> TenantWecomAppDto.Response.of(a, buildCallbackUrl(tenantCode), null))
                .toList();
    }

    public TenantWecomAppDto.Response get(Long id) {
        Long tenantId = TenantContext.require();
        TenantWecomApp a = loadOwned(id, tenantId);
        return TenantWecomAppDto.Response.of(a, buildCallbackUrl(tenantCode(tenantId)), null);
    }

    @Transactional
    public TenantWecomAppDto.Response create(TenantWecomAppDto.Request req, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        validate(req, true);

        repo.findByCorpIdAndAgentId(req.getCorpId(), req.getAgentId()).ifPresent(existed -> {
            throw BizException.conflict("该 corpId + agentId 组合已被占用");
        });

        TenantWecomApp a = new TenantWecomApp();
        a.setTenantId(tenantId);
        a.setName(nonBlank(req.getName(), "默认应用"));
        a.setCorpId(req.getCorpId().trim());
        a.setAgentId(req.getAgentId());
        a.setToken(req.getToken().trim());
        a.setAesKeyCipher(cipher.encrypt(req.getAesKey().trim()));
        a.setSecretCipher(cipher.encrypt(req.getSecret().trim()));
        a.setBotUserid(blankToNull(req.getBotUserid()));
        a.setApiBase(blankToNull(req.getApiBase()));
        a.setEnabled(req.getEnabled() == null || req.getEnabled());
        a.setStatus(TenantWecomApp.STATUS_NOT_VERIFIED);
        a.setUpdatedAt(OffsetDateTime.now());
        a = repo.save(a);

        audit.record(op, "WECOM_APP_CREATE", "tenant_wecom_app", String.valueOf(a.getId()),
                null, Map.of("name", a.getName(), "corpId", a.getCorpId(), "agentId", a.getAgentId()));
        return TenantWecomAppDto.Response.of(a, buildCallbackUrl(tenantCode(tenantId)), null);
    }

    @Transactional
    public TenantWecomAppDto.Response update(Long id, TenantWecomAppDto.Request req, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        validate(req, false);
        TenantWecomApp a = loadOwned(id, tenantId);
        Map<String, Object> before = snapshot(a);

        if (req.getName() != null) a.setName(req.getName());
        if (req.getCorpId() != null) a.setCorpId(req.getCorpId().trim());
        if (req.getAgentId() != null) a.setAgentId(req.getAgentId());
        if (req.getToken() != null && !req.getToken().isBlank()) a.setToken(req.getToken().trim());
        if (req.getAesKey() != null && !req.getAesKey().isBlank()) {
            a.setAesKeyCipher(cipher.encrypt(req.getAesKey().trim()));
            // aesKey 变化 → 重新验证
            a.setStatus(TenantWecomApp.STATUS_NOT_VERIFIED);
            a.setVerifiedAt(null);
        }
        if (req.getSecret() != null && !req.getSecret().isBlank()) {
            a.setSecretCipher(cipher.encrypt(req.getSecret().trim()));
        }
        if (req.getBotUserid() != null) a.setBotUserid(blankToNull(req.getBotUserid()));
        if (req.getApiBase() != null) a.setApiBase(blankToNull(req.getApiBase()));
        if (req.getEnabled() != null) a.setEnabled(req.getEnabled());
        a.setUpdatedAt(OffsetDateTime.now());

        a = repo.save(a);
        audit.record(op, "WECOM_APP_UPDATE", "tenant_wecom_app", String.valueOf(id), before, snapshot(a));
        return TenantWecomAppDto.Response.of(a, buildCallbackUrl(tenantCode(tenantId)), null);
    }

    @Transactional
    public void delete(Long id, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        TenantWecomApp a = loadOwned(id, tenantId);
        if (a.getCsAgentId() != null) {
            throw BizException.conflict("该应用已绑定智能客服，请先解绑");
        }
        repo.delete(a);
        audit.record(op, "WECOM_APP_DELETE", "tenant_wecom_app", String.valueOf(id), snapshot(a), null);
    }

    /** POST /{id}/test — 用本配置调企微 /cgi-bin/gettoken 验证 corpId + secret。 */
    @Transactional
    public TenantWecomAppDto.TestResult test(Long id, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        TenantWecomApp a = loadOwned(id, tenantId);

        long started = System.currentTimeMillis();
        TenantWecomAppDto.TestResult tr = new TenantWecomAppDto.TestResult();
        String secret;
        try {
            secret = cipher.decrypt(a.getSecretCipher());
        } catch (Exception e) {
            tr.setOk(false);
            tr.setMessage("密文解密失败：请删除并重新录入 Secret。");
            tr.setLatencyMs(System.currentTimeMillis() - started);
            recordTest(a, tr);
            return tr;
        }

        try {
            WebClient wc = webClientBuilder.clone()
                    .baseUrl(nonBlank(a.getApiBase(), nonBlank(props.getWecom().getApiBase(), "https://qyapi.weixin.qq.com")))
                    .defaultHeader("Content-Type", "application/json")
                    .build();
            JsonNode resp = wc.get()
                    .uri(u -> u.path("/cgi-bin/gettoken")
                            .queryParam("corpid", a.getCorpId())
                            .queryParam("corpsecret", secret)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(8));
            if (resp == null) {
                throw new RuntimeException("empty response");
            }
            int code = resp.path("errcode").asInt(-1);
            if (code == 0) {
                tr.setOk(true);
                tr.setMessage("OK");
                // 连通性通过不等于验签通过；status 维持不动（由 verify 回调写）
            } else {
                tr.setOk(false);
                tr.setMessage("errcode=" + code + ", errmsg=" + resp.path("errmsg").asText());
                a.setStatus(TenantWecomApp.STATUS_FAILED);
            }
        } catch (Exception e) {
            tr.setOk(false);
            tr.setMessage(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        tr.setLatencyMs(System.currentTimeMillis() - started);
        recordTest(a, tr);
        audit.record(op, "WECOM_APP_TEST", "tenant_wecom_app", String.valueOf(id),
                null, Map.of("ok", tr.isOk(), "latencyMs", tr.getLatencyMs()));
        return tr;
    }

    /** GET /{id}/callback-url — 便于前端无需猜路径。 */
    public TenantWecomAppDto.CallbackUrlResponse callbackUrl(Long id) {
        Long tenantId = TenantContext.require();
        loadOwned(id, tenantId);
        TenantWecomAppDto.CallbackUrlResponse r = new TenantWecomAppDto.CallbackUrlResponse();
        r.setCallbackUrl(buildCallbackUrl(tenantCode(tenantId)));
        return r;
    }

    /**
     * 企微回调验签首次通过时由 {@code WeComCallbackController.verifyTenant} 调用。
     * 把本租户已 enabled 的匹配 app 置为 VERIFIED。
     */
    @Transactional
    public void markVerified(Long wecomAppId) {
        repo.findById(wecomAppId).ifPresent(a -> {
            if (TenantWecomApp.STATUS_VERIFIED.equals(a.getStatus())) return;
            a.setStatus(TenantWecomApp.STATUS_VERIFIED);
            a.setVerifiedAt(OffsetDateTime.now());
            a.setUpdatedAt(OffsetDateTime.now());
            repo.save(a);
            log.info("tenant_wecom_app id={} marked VERIFIED", wecomAppId);
        });
    }

    // ---- helpers ----

    private TenantWecomApp loadOwned(Long id, Long tenantId) {
        TenantWecomApp a = repo.findById(id)
                .orElseThrow(() -> BizException.notFound("wecom app not found"));
        if (!tenantId.equals(a.getTenantId())) {
            throw BizException.notFound("wecom app not found");
        }
        return a;
    }

    private void validate(TenantWecomAppDto.Request req, boolean isCreate) {
        if (req == null) throw BizException.of("body required");
        if (isCreate) {
            if (isBlank(req.getCorpId())) throw BizException.of("corpId required");
            if (req.getAgentId() == null) throw BizException.of("agentId required");
            if (isBlank(req.getToken())) throw BizException.of("token required");
            if (isBlank(req.getAesKey())) throw BizException.of("aesKey required");
            if (isBlank(req.getSecret())) throw BizException.of("secret required");
            if (req.getAesKey().trim().length() != 43) throw BizException.of("aesKey 必须为 43 位");
        } else {
            // 可选字段，除 corpId/agentId 一旦填写校验非空
            if (req.getCorpId() != null && req.getCorpId().isBlank()) throw BizException.of("corpId 不能为空串");
            if (req.getAesKey() != null && !req.getAesKey().isBlank() && req.getAesKey().trim().length() != 43) {
                throw BizException.of("aesKey 必须为 43 位");
            }
        }
    }

    private void recordTest(TenantWecomApp a, TenantWecomAppDto.TestResult tr) {
        a.setLastTestAt(OffsetDateTime.now());
        a.setLastTestOk(tr.isOk());
        a.setLastTestMsg(trunc(tr.getMessage(), 400));
        a.setUpdatedAt(OffsetDateTime.now());
        repo.save(a);
    }

    private String buildCallbackUrl(String tenantCode) {
        String base = props.getPublicBaseUrl();
        if (base == null || base.isBlank()) return "(尚未配置 app.public-base-url)";
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + "/api/wecom/callback/" + tenantCode;
    }

    private String tenantCode(Long tenantId) {
        return tenantRepo.findById(tenantId).map(Tenant::getCode).orElse("unknown");
    }

    private Map<String, Object> snapshot(TenantWecomApp a) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", a.getName());
        m.put("corpId", a.getCorpId());
        m.put("agentId", a.getAgentId());
        m.put("enabled", a.getEnabled());
        m.put("botUserid", a.getBotUserid());
        m.put("status", a.getStatus());
        return m;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String blankToNull(String s) { return isBlank(s) ? null : s.trim(); }
    private static String nonBlank(String s, String def) { return isBlank(s) ? def : s; }
    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
