package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.common.PageResult;
import com.aics.common.tenant.TenantContext;
import com.aics.m005_admin.audit.AdminAuditLog;
import com.aics.m005_admin.audit.AdminAuditLogRepository;
import com.aics.m005_admin.audit.LlmInvocation;
import com.aics.m005_admin.audit.LlmInvocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final LlmInvocationRepository llmRepo;
    private final AdminAuditLogRepository adminAuditRepo;

    @GetMapping("/llm")
    @PreAuthorize("hasAuthority('audit:session:read')")
    public ApiResponse<PageResult<LlmInvocation>> listLlm(@RequestParam(required = false) String groupId,
                                                          @RequestParam(required = false) String status,
                                                          @RequestParam(required = false, defaultValue = "30") int days,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        Long tenantId = TenantContext.require();
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size);
        OffsetDateTime from = OffsetDateTime.now().minusDays(days);
        OffsetDateTime to = OffsetDateTime.now();
        String g = (groupId == null || groupId.isBlank()) ? null : groupId;
        String st = (status == null || status.isBlank()) ? null : status;
        Page<LlmInvocation> pg = llmRepo.search(tenantId, g, from, to, st, pr);
        return ApiResponse.ok(PageResult.of(pg));
    }

    @GetMapping("/llm/{id}")
    @PreAuthorize("hasAuthority('audit:session:read')")
    public ApiResponse<LlmInvocation> detail(@PathVariable Long id) {
        Long tenantId = TenantContext.require();
        return ApiResponse.ok(llmRepo.findById(id)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElse(null));
    }

    @GetMapping("/admin-logs")
    @PreAuthorize("hasAuthority('audit:session:read')")
    public ApiResponse<PageResult<AdminAuditLog>> adminLogs(@RequestParam(required = false) Long adminId,
                                                            @RequestParam(required = false) String action,
                                                            @RequestParam(defaultValue = "30") int days,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        Long tenantId = TenantContext.require();
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size);
        OffsetDateTime from = OffsetDateTime.now().minusDays(days);
        String ac = (action == null || action.isBlank()) ? null : action;
        Page<AdminAuditLog> pg = adminAuditRepo.search(tenantId, adminId, ac, from, pr);
        return ApiResponse.ok(PageResult.of(pg));
    }
}
