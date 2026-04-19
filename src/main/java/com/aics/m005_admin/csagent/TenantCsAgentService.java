package com.aics.m005_admin.csagent;

import com.aics.common.BizException;
import com.aics.common.tenant.TenantContext;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.tenant.TenantCsAgent;
import com.aics.m005_admin.tenant.TenantCsAgentRepository;
import com.aics.m005_admin.tenant.TenantLlmConfig;
import com.aics.m005_admin.tenant.TenantLlmConfigRepository;
import com.aics.m005_admin.tenant.TenantWecomApp;
import com.aics.m005_admin.tenant.TenantWecomAppRepository;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * P004-A F002：租户智能客服（CS Agent）实体管理 + 与企微应用 1:1 绑定。
 * 一个租户下多个 Agent；每个 Agent 最多绑一个 wecomApp（由 uk_wecom_app_cs_agent 保证）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCsAgentService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,31}$");

    private final TenantCsAgentRepository repo;
    private final TenantWecomAppRepository wecomRepo;
    private final TenantLlmConfigRepository llmRepo;
    private final AdminAuditLogger audit;

    public List<TenantCsAgentDto.Response> list() {
        Long tenantId = TenantContext.require();
        List<TenantCsAgent> agents = repo.findByTenantIdOrderByIdDesc(tenantId);
        if (agents.isEmpty()) return List.of();
        List<TenantWecomApp> apps = wecomRepo.findByTenantIdOrderByIdAsc(tenantId);
        return agents.stream().map(a -> enrich(a, apps)).toList();
    }

    public TenantCsAgentDto.Response get(Long id) {
        Long tenantId = TenantContext.require();
        TenantCsAgent a = loadOwned(id, tenantId);
        return enrich(a, wecomRepo.findByTenantIdOrderByIdAsc(tenantId));
    }

    @Transactional
    public TenantCsAgentDto.Response create(TenantCsAgentDto.Request req, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        validate(req, true, tenantId, null);

        TenantCsAgent a = new TenantCsAgent();
        a.setTenantId(tenantId);
        a.setName(req.getName().trim());
        a.setCode(req.getCode().trim());
        a.setDescription(blankToNull(req.getDescription()));
        a.setAvatarUrl(blankToNull(req.getAvatarUrl()));
        a.setPersonaPrompt(blankToNull(req.getPersonaPrompt()));
        a.setGreeting(blankToNull(req.getGreeting()));
        a.setChatLlmConfigId(req.getChatLlmConfigId());
        a.setEnabled(req.getEnabled() == null || req.getEnabled());
        a.setCreatedBy(op != null ? op.id() : null);
        a.setUpdatedAt(OffsetDateTime.now());
        a = repo.save(a);

        audit.record(op, "CS_AGENT_CREATE", "tenant_cs_agent", String.valueOf(a.getId()),
                null, Map.of("name", a.getName(), "code", a.getCode()));
        return enrich(a, wecomRepo.findByTenantIdOrderByIdAsc(tenantId));
    }

    @Transactional
    public TenantCsAgentDto.Response update(Long id, TenantCsAgentDto.Request req, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        TenantCsAgent a = loadOwned(id, tenantId);
        validate(req, false, tenantId, a);
        Map<String, Object> before = snapshot(a);

        if (req.getName() != null) a.setName(req.getName().trim());
        if (req.getCode() != null) a.setCode(req.getCode().trim());
        if (req.getDescription() != null) a.setDescription(blankToNull(req.getDescription()));
        if (req.getAvatarUrl() != null) a.setAvatarUrl(blankToNull(req.getAvatarUrl()));
        if (req.getPersonaPrompt() != null) a.setPersonaPrompt(blankToNull(req.getPersonaPrompt()));
        if (req.getGreeting() != null) a.setGreeting(blankToNull(req.getGreeting()));
        if (req.getChatLlmConfigId() != null) a.setChatLlmConfigId(req.getChatLlmConfigId());
        if (req.getEnabled() != null) a.setEnabled(req.getEnabled());
        a.setUpdatedAt(OffsetDateTime.now());

        a = repo.save(a);
        audit.record(op, "CS_AGENT_UPDATE", "tenant_cs_agent", String.valueOf(id), before, snapshot(a));
        return enrich(a, wecomRepo.findByTenantIdOrderByIdAsc(tenantId));
    }

    @Transactional
    public void delete(Long id, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        TenantCsAgent a = loadOwned(id, tenantId);
        wecomRepo.findByCsAgentId(id).ifPresent(app -> {
            throw BizException.conflict("该 Agent 已绑定企微应用「" + app.getName() + "」，请先解绑");
        });
        repo.delete(a);
        audit.record(op, "CS_AGENT_DELETE", "tenant_cs_agent", String.valueOf(id), snapshot(a), null);
    }

    /** 把当前 tenant 的一个 wecomApp 绑到本 Agent（或替换已有绑定）。 */
    @Transactional
    public TenantCsAgentDto.Response bindWecomApp(Long agentId, Long wecomAppId, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        TenantCsAgent agent = loadOwned(agentId, tenantId);
        TenantWecomApp app = wecomRepo.findById(wecomAppId)
                .orElseThrow(() -> BizException.notFound("wecom app not found"));
        if (!tenantId.equals(app.getTenantId())) {
            throw BizException.notFound("wecom app not found");
        }
        if (app.getCsAgentId() != null && !app.getCsAgentId().equals(agentId)) {
            throw BizException.conflict("该企微应用已绑定到其他 Agent，请先从那个 Agent 解绑");
        }
        // Agent 本身最多绑一个 wecomApp（uk_wecom_app_cs_agent 保证全局唯一）。
        wecomRepo.findByCsAgentId(agentId).ifPresent(existing -> {
            if (!existing.getId().equals(wecomAppId)) {
                throw BizException.conflict("该 Agent 已绑定企微应用「" + existing.getName() + "」，请先解绑");
            }
        });

        app.setCsAgentId(agentId);
        wecomRepo.save(app);
        audit.record(op, "CS_AGENT_BIND_WECOM", "tenant_cs_agent", String.valueOf(agentId),
                null, Map.of("wecomAppId", wecomAppId, "wecomAppName", app.getName()));
        return enrich(agent, wecomRepo.findByTenantIdOrderByIdAsc(tenantId));
    }

    @Transactional
    public TenantCsAgentDto.Response unbindWecomApp(Long agentId, Long wecomAppId, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        TenantCsAgent agent = loadOwned(agentId, tenantId);
        TenantWecomApp app = wecomRepo.findById(wecomAppId)
                .orElseThrow(() -> BizException.notFound("wecom app not found"));
        if (!tenantId.equals(app.getTenantId())) {
            throw BizException.notFound("wecom app not found");
        }
        if (!agentId.equals(app.getCsAgentId())) {
            throw BizException.of("该企微应用并未绑定到此 Agent");
        }
        app.setCsAgentId(null);
        wecomRepo.save(app);
        audit.record(op, "CS_AGENT_UNBIND_WECOM", "tenant_cs_agent", String.valueOf(agentId),
                Map.of("wecomAppId", wecomAppId), null);
        return enrich(agent, wecomRepo.findByTenantIdOrderByIdAsc(tenantId));
    }

    // ---- helpers ----

    private TenantCsAgent loadOwned(Long id, Long tenantId) {
        TenantCsAgent a = repo.findById(id)
                .orElseThrow(() -> BizException.notFound("cs agent not found"));
        if (!tenantId.equals(a.getTenantId())) {
            throw BizException.notFound("cs agent not found");
        }
        return a;
    }

    private TenantCsAgentDto.Response enrich(TenantCsAgent a, List<TenantWecomApp> apps) {
        TenantCsAgentDto.Response r = TenantCsAgentDto.Response.of(a);
        for (TenantWecomApp app : apps) {
            if (a.getId().equals(app.getCsAgentId())) {
                r.setBoundWecomAppId(app.getId());
                r.setBoundWecomAppName(app.getName());
                break;
            }
        }
        if (a.getChatLlmConfigId() != null) {
            llmRepo.findByIdAndTenantId(a.getChatLlmConfigId(), a.getTenantId())
                    .ifPresent(c -> r.setChatLlmConfigLabel(c.getProvider() + " / " + c.getModel()));
        }
        return r;
    }

    private void validate(TenantCsAgentDto.Request req, boolean isCreate, Long tenantId, TenantCsAgent current) {
        if (req == null) throw BizException.of("body required");
        if (isCreate) {
            if (isBlank(req.getName())) throw BizException.of("name required");
            if (isBlank(req.getCode())) throw BizException.of("code required");
        }
        if (req.getName() != null && req.getName().trim().length() > 64) {
            throw BizException.of("name 最长 64");
        }
        if (req.getCode() != null) {
            String code = req.getCode().trim();
            if (!CODE_PATTERN.matcher(code).matches()) {
                throw BizException.of("code 必须为小写字母开头的英文 slug（a-z0-9_），2-32 位");
            }
            boolean changing = isCreate || current == null || !code.equals(current.getCode());
            if (changing && repo.existsByTenantIdAndCode(tenantId, code)) {
                throw BizException.conflict("code 在本租户下已存在");
            }
        }
        if (req.getChatLlmConfigId() != null) {
            TenantLlmConfig c = llmRepo.findByIdAndTenantId(req.getChatLlmConfigId(), tenantId)
                    .orElseThrow(() -> BizException.of("chatLlmConfigId 非法或不属于当前租户"));
            if (!TenantLlmConfig.PURPOSE_CHAT.equals(c.getPurpose())) {
                throw BizException.of("chatLlmConfigId 必须指向 purpose=chat 的配置");
            }
        }
    }

    private Map<String, Object> snapshot(TenantCsAgent a) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", a.getName());
        m.put("code", a.getCode());
        m.put("enabled", a.getEnabled());
        m.put("chatLlmConfigId", a.getChatLlmConfigId());
        return m;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String blankToNull(String s) { return isBlank(s) ? null : s.trim(); }
}
