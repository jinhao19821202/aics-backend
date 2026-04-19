package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.common.BizException;
import com.aics.common.PageResult;
import com.aics.common.tenant.TenantContext;
import com.aics.m004_handoff.domain.GroupAgentMapping;
import com.aics.m004_handoff.domain.GroupAgentMappingRepository;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/group-mappings")
@RequiredArgsConstructor
public class GroupMappingController {

    private final GroupAgentMappingRepository repo;
    private final AdminAuditLogger audit;

    @GetMapping
    @PreAuthorize("hasAuthority('group:manage') or hasAuthority('handoff:manage')")
    public ApiResponse<PageResult<GroupAgentMapping>> list(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size);
        Page<GroupAgentMapping> pg = repo.findAll(pr);
        return ApiResponse.ok(PageResult.of(pg));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('group:manage')")
    @Transactional
    public ApiResponse<GroupAgentMapping> upsert(@RequestBody GroupAgentMapping req,
                                                 @AuthenticationPrincipal AdminPrincipal op) {
        if (req.getGroupId() == null || req.getGroupId().isBlank()) throw BizException.of("groupId 必填");
        Long tenantId = TenantContext.require();
        GroupAgentMapping g = repo.findByTenantIdAndGroupId(tenantId, req.getGroupId()).orElseGet(GroupAgentMapping::new);
        boolean isNew = g.getId() == null;
        g.setTenantId(tenantId);
        g.setGroupId(req.getGroupId());
        g.setGroupName(req.getGroupName());
        g.setAgentUserids(req.getAgentUserids() == null ? List.of() : req.getAgentUserids());
        g.setDefaultAgent(req.getDefaultAgent());
        g.setUpdatedAt(OffsetDateTime.now());
        g = repo.save(g);
        audit.record(op, isNew ? "group.create" : "group.update", "group_agent_mapping",
                String.valueOf(g.getId()), null,
                Map.of("groupId", g.getGroupId(), "agents", g.getAgentUserids()));
        return ApiResponse.ok(g);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('group:manage')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal op) {
        repo.findById(id).ifPresent(g -> {
            repo.delete(g);
            audit.record(op, "group.delete", "group_agent_mapping", String.valueOf(id),
                    Map.of("groupId", g.getGroupId()), null);
        });
        return ApiResponse.ok();
    }
}
