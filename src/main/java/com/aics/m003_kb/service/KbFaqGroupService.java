package com.aics.m003_kb.service;

import com.aics.common.BizException;
import com.aics.common.tenant.TenantContext;
import com.aics.m003_kb.domain.KbFaqGroup;
import com.aics.m003_kb.domain.KbFaqGroupRepository;
import com.aics.m003_kb.domain.KbFaqRepository;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P004-B F003：租户 FAQ 分组管理。
 * 分组删除策略：有 FAQ 归属则拒绝，引导用户先迁移或改绑。默认分组不允许删除（兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbFaqGroupService {

    private static final String DEFAULT_GROUP_NAME = "默认分组";

    private final KbFaqGroupRepository repo;
    private final KbFaqRepository faqRepo;
    private final AdminAuditLogger audit;

    public List<KbFaqGroupDto.Response> list() {
        Long tenantId = TenantContext.require();
        List<KbFaqGroup> groups = repo.findByTenantIdOrderBySortOrderAscIdAsc(tenantId);
        return groups.stream()
                .map(g -> KbFaqGroupDto.Response.of(g, faqRepo.countByGroupId(g.getId())))
                .toList();
    }

    public KbFaqGroupDto.Response get(Long id) {
        Long tenantId = TenantContext.require();
        KbFaqGroup g = loadOwned(id, tenantId);
        return KbFaqGroupDto.Response.of(g, faqRepo.countByGroupId(g.getId()));
    }

    @Transactional
    public KbFaqGroupDto.Response create(KbFaqGroupDto.Request req, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        validate(req, true, tenantId, null);

        KbFaqGroup g = new KbFaqGroup();
        g.setTenantId(tenantId);
        g.setName(req.getName().trim());
        g.setDescription(blankToNull(req.getDescription()));
        g.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        g.setCreatedBy(op != null ? op.id() : null);
        g.setUpdatedAt(OffsetDateTime.now());
        g = repo.save(g);

        audit.record(op, "FAQ_GROUP_CREATE", "kb_faq_group", String.valueOf(g.getId()),
                null, Map.of("name", g.getName()));
        return KbFaqGroupDto.Response.of(g, 0);
    }

    @Transactional
    public KbFaqGroupDto.Response update(Long id, KbFaqGroupDto.Request req, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        KbFaqGroup g = loadOwned(id, tenantId);
        validate(req, false, tenantId, g);
        Map<String, Object> before = snapshot(g);

        if (req.getName() != null) g.setName(req.getName().trim());
        if (req.getDescription() != null) g.setDescription(blankToNull(req.getDescription()));
        if (req.getSortOrder() != null) g.setSortOrder(req.getSortOrder());
        g.setUpdatedAt(OffsetDateTime.now());

        g = repo.save(g);
        audit.record(op, "FAQ_GROUP_UPDATE", "kb_faq_group", String.valueOf(id), before, snapshot(g));
        return KbFaqGroupDto.Response.of(g, faqRepo.countByGroupId(g.getId()));
    }

    @Transactional
    public void delete(Long id, AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        KbFaqGroup g = loadOwned(id, tenantId);
        if (DEFAULT_GROUP_NAME.equals(g.getName())) {
            throw BizException.conflict("默认分组不允许删除");
        }
        long count = faqRepo.countByGroupId(id);
        if (count > 0) {
            throw BizException.conflict("该分组下仍有 " + count + " 条 FAQ，请先迁移或改绑");
        }
        repo.delete(g);
        audit.record(op, "FAQ_GROUP_DELETE", "kb_faq_group", String.valueOf(id), snapshot(g), null);
    }

    /** 返回当前租户的「默认分组」id；没有则新建。用于 FaqService 老接口兼容。 */
    @Transactional
    public Long ensureDefaultGroupId(Long tenantId) {
        return repo.findByTenantIdAndName(tenantId, DEFAULT_GROUP_NAME)
                .map(KbFaqGroup::getId)
                .orElseGet(() -> {
                    KbFaqGroup g = new KbFaqGroup();
                    g.setTenantId(tenantId);
                    g.setName(DEFAULT_GROUP_NAME);
                    g.setDescription("系统自动创建的默认分组");
                    g.setSortOrder(0);
                    g.setUpdatedAt(OffsetDateTime.now());
                    return repo.save(g).getId();
                });
    }

    // ---- helpers ----

    private KbFaqGroup loadOwned(Long id, Long tenantId) {
        KbFaqGroup g = repo.findById(id)
                .orElseThrow(() -> BizException.notFound("faq group not found"));
        if (!tenantId.equals(g.getTenantId())) {
            throw BizException.notFound("faq group not found");
        }
        return g;
    }

    private void validate(KbFaqGroupDto.Request req, boolean isCreate, Long tenantId, KbFaqGroup current) {
        if (req == null) throw BizException.of("body required");
        if (isCreate && isBlank(req.getName())) throw BizException.of("name required");
        if (req.getName() != null) {
            String name = req.getName().trim();
            if (name.isEmpty() || name.length() > 64) throw BizException.of("name 长度 1-64");
            boolean changing = isCreate || current == null || !name.equals(current.getName());
            if (changing && repo.existsByTenantIdAndName(tenantId, name)) {
                throw BizException.conflict("分组名在本租户下已存在");
            }
        }
    }

    private Map<String, Object> snapshot(KbFaqGroup g) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", g.getName());
        m.put("description", g.getDescription());
        m.put("sortOrder", g.getSortOrder());
        return m;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String blankToNull(String s) { return isBlank(s) ? null : s.trim(); }
}
