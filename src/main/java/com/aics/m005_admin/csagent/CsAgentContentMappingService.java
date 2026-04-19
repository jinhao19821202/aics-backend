package com.aics.m005_admin.csagent;

import com.aics.common.BizException;
import com.aics.common.tenant.TenantContext;
import com.aics.m003_kb.domain.KbDocument;
import com.aics.m003_kb.domain.KbDocumentRepository;
import com.aics.m003_kb.domain.KbFaqGroup;
import com.aics.m003_kb.domain.KbFaqGroupRepository;
import com.aics.m003_kb.domain.KbFaqRepository;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.tenant.TenantCsAgent;
import com.aics.m005_admin.tenant.TenantCsAgentRepository;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * P004-B F004：智能客服（CS Agent）内容归属。
 * 语义：Agent 未配置任何映射（count == 0）等价于「无限制，能看到本租户全部」；
 * 一旦配置了至少一条映射，则仅能看到被映射的 Group/Document。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CsAgentContentMappingService {

    private final CsAgentFaqGroupMappingRepository faqGroupMapRepo;
    private final CsAgentKbDocumentMappingRepository docMapRepo;
    private final TenantCsAgentRepository agentRepo;
    private final KbFaqGroupRepository faqGroupRepo;
    private final KbFaqRepository faqRepo;
    private final KbDocumentRepository docRepo;
    private final AdminAuditLogger audit;

    // --------------------------------------------------------------------
    // 查询（管理后台）
    // --------------------------------------------------------------------

    public List<CsAgentContentMappingDto.FaqGroupItem> listFaqGroups(Long agentId) {
        Long tenantId = TenantContext.require();
        assertAgentOwned(agentId, tenantId);
        List<CsAgentFaqGroupMapping> rows = faqGroupMapRepo.findByCsAgentId(agentId);
        if (rows.isEmpty()) return List.of();
        Set<Long> ids = new HashSet<>();
        for (var r : rows) ids.add(r.getFaqGroupId());
        Map<Long, KbFaqGroup> groups = new HashMap<>();
        for (KbFaqGroup g : faqGroupRepo.findAllById(ids)) {
            if (tenantId.equals(g.getTenantId())) groups.put(g.getId(), g);
        }
        List<CsAgentContentMappingDto.FaqGroupItem> out = new ArrayList<>(rows.size());
        for (var r : rows) {
            KbFaqGroup g = groups.get(r.getFaqGroupId());
            if (g == null) continue;
            CsAgentContentMappingDto.FaqGroupItem item = new CsAgentContentMappingDto.FaqGroupItem();
            item.setFaqGroupId(g.getId());
            item.setName(g.getName());
            item.setFaqCount(faqRepo.countByGroupId(g.getId()));
            item.setCreatedAt(r.getCreatedAt());
            out.add(item);
        }
        return out;
    }

    public List<CsAgentContentMappingDto.DocumentItem> listDocuments(Long agentId) {
        Long tenantId = TenantContext.require();
        assertAgentOwned(agentId, tenantId);
        List<CsAgentKbDocumentMapping> rows = docMapRepo.findByCsAgentId(agentId);
        if (rows.isEmpty()) return List.of();
        Set<Long> ids = new HashSet<>();
        for (var r : rows) ids.add(r.getKbDocumentId());
        Map<Long, KbDocument> docs = new HashMap<>();
        for (KbDocument d : docRepo.findAllById(ids)) {
            if (tenantId.equals(d.getTenantId()) && !Boolean.TRUE.equals(d.getDeleted())) {
                docs.put(d.getId(), d);
            }
        }
        List<CsAgentContentMappingDto.DocumentItem> out = new ArrayList<>(rows.size());
        for (var r : rows) {
            KbDocument d = docs.get(r.getKbDocumentId());
            if (d == null) continue;
            CsAgentContentMappingDto.DocumentItem item = new CsAgentContentMappingDto.DocumentItem();
            item.setKbDocumentId(d.getId());
            item.setTitle(d.getTitle());
            item.setStatus(d.getStatus());
            item.setCreatedAt(r.getCreatedAt());
            out.add(item);
        }
        return out;
    }

    // --------------------------------------------------------------------
    // 批量替换：PUT 语义，前端一次性提交完整 ids。
    // --------------------------------------------------------------------

    @Transactional
    public List<CsAgentContentMappingDto.FaqGroupItem> replaceFaqGroups(Long agentId,
                                                                       List<Long> ids,
                                                                       AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        assertAgentOwned(agentId, tenantId);
        Set<Long> target = normalizeIds(ids);
        if (!target.isEmpty()) {
            List<KbFaqGroup> groups = faqGroupRepo.findAllById(target);
            if (groups.size() != target.size()) {
                throw BizException.of("存在非法 faqGroupId（可能不属于当前租户或已删除）");
            }
            for (KbFaqGroup g : groups) {
                if (!tenantId.equals(g.getTenantId())) {
                    throw BizException.forbidden("不能映射跨租户的 FAQ 分组：id=" + g.getId());
                }
            }
        }

        Set<Long> existing = new HashSet<>();
        List<CsAgentFaqGroupMapping> current = faqGroupMapRepo.findByCsAgentId(agentId);
        for (var m : current) existing.add(m.getFaqGroupId());

        Set<Long> toDelete = new HashSet<>(existing);
        toDelete.removeAll(target);
        Set<Long> toInsert = new HashSet<>(target);
        toInsert.removeAll(existing);

        if (!toDelete.isEmpty()) {
            faqGroupMapRepo.deleteByCsAgentIdAndFaqGroupIdIn(agentId, toDelete);
        }
        Long userId = op != null ? op.id() : null;
        for (Long gid : toInsert) {
            CsAgentFaqGroupMapping m = new CsAgentFaqGroupMapping();
            m.setTenantId(tenantId);
            m.setCsAgentId(agentId);
            m.setFaqGroupId(gid);
            m.setCreatedBy(userId);
            faqGroupMapRepo.save(m);
        }

        audit.record(op, "CS_AGENT_FAQ_GROUP_REPLACE", "tenant_cs_agent",
                String.valueOf(agentId),
                Map.of("faqGroupIds", new ArrayList<>(existing)),
                Map.of("faqGroupIds", new ArrayList<>(target)));
        return listFaqGroups(agentId);
    }

    @Transactional
    public List<CsAgentContentMappingDto.DocumentItem> replaceDocuments(Long agentId,
                                                                       List<Long> ids,
                                                                       AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        assertAgentOwned(agentId, tenantId);
        Set<Long> target = normalizeIds(ids);
        if (!target.isEmpty()) {
            List<KbDocument> docs = docRepo.findAllById(target);
            if (docs.size() != target.size()) {
                throw BizException.of("存在非法 kbDocumentId（可能不属于当前租户或已删除）");
            }
            for (KbDocument d : docs) {
                if (!tenantId.equals(d.getTenantId())) {
                    throw BizException.forbidden("不能映射跨租户的文档：id=" + d.getId());
                }
                if (Boolean.TRUE.equals(d.getDeleted())) {
                    throw BizException.of("文档已删除：id=" + d.getId());
                }
            }
        }

        Set<Long> existing = new HashSet<>();
        List<CsAgentKbDocumentMapping> current = docMapRepo.findByCsAgentId(agentId);
        for (var m : current) existing.add(m.getKbDocumentId());

        Set<Long> toDelete = new HashSet<>(existing);
        toDelete.removeAll(target);
        Set<Long> toInsert = new HashSet<>(target);
        toInsert.removeAll(existing);

        if (!toDelete.isEmpty()) {
            docMapRepo.deleteByCsAgentIdAndKbDocumentIdIn(agentId, toDelete);
        }
        Long userId = op != null ? op.id() : null;
        for (Long did : toInsert) {
            CsAgentKbDocumentMapping m = new CsAgentKbDocumentMapping();
            m.setTenantId(tenantId);
            m.setCsAgentId(agentId);
            m.setKbDocumentId(did);
            m.setCreatedBy(userId);
            docMapRepo.save(m);
        }

        audit.record(op, "CS_AGENT_DOCUMENT_REPLACE", "tenant_cs_agent",
                String.valueOf(agentId),
                Map.of("kbDocumentIds", new ArrayList<>(existing)),
                Map.of("kbDocumentIds", new ArrayList<>(target)));
        return listDocuments(agentId);
    }

    // --------------------------------------------------------------------
    // 检索路径：返回 agent 能看到的 id 白名单；null/空集合 = 无限制（P004-B F004 语义）。
    // 这两个方法不校验 TenantContext（检索链路调用时已有租户上下文由上层保证），
    // 但为了防御性返回空 list 而非抛异常。
    // --------------------------------------------------------------------

    public Set<Long> allowedFaqGroupIds(Long csAgentId) {
        if (csAgentId == null) return null;
        List<CsAgentFaqGroupMapping> rows = faqGroupMapRepo.findByCsAgentId(csAgentId);
        if (rows.isEmpty()) return null; // null = 无限制
        Set<Long> ids = new HashSet<>();
        for (var r : rows) ids.add(r.getFaqGroupId());
        return ids;
    }

    public Set<Long> allowedDocumentIds(Long csAgentId) {
        if (csAgentId == null) return null;
        List<CsAgentKbDocumentMapping> rows = docMapRepo.findByCsAgentId(csAgentId);
        if (rows.isEmpty()) return null; // null = 无限制
        Set<Long> ids = new HashSet<>();
        for (var r : rows) ids.add(r.getKbDocumentId());
        return ids;
    }

    // --------------------------------------------------------------------
    // helpers
    // --------------------------------------------------------------------

    private void assertAgentOwned(Long agentId, Long tenantId) {
        TenantCsAgent a = agentRepo.findById(agentId)
                .orElseThrow(() -> BizException.notFound("cs agent not found"));
        if (!tenantId.equals(a.getTenantId())) {
            throw BizException.notFound("cs agent not found");
        }
    }

    private static Set<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Set.of();
        Set<Long> out = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) out.add(id);
        }
        return out;
    }
}
