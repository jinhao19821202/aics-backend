package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.common.tenant.TenantContext;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.tenant.ReindexService;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/reindex")
@RequiredArgsConstructor
public class ReindexController {

    private final ReindexService reindexService;
    private final AdminAuditLogger audit;

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:kb:manage') or hasAuthority('tenant:model:config')")
    public ApiResponse<Void> trigger(@RequestParam(required = false) Integer embeddingDim,
                                     @AuthenticationPrincipal AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        reindexService.markRunning(tenantId);
        reindexService.triggerAsync(tenantId, embeddingDim);
        audit.record(op, "KB_REINDEX_TRIGGER", "tenant", String.valueOf(tenantId),
                null, Map.of("embeddingDim", embeddingDim == null ? "unchanged" : embeddingDim));
        return ApiResponse.ok(null);
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('tenant:kb:read') or hasAuthority('tenant:model:view')")
    public ApiResponse<ReindexService.ReindexStatus> status() {
        Long tenantId = TenantContext.require();
        return ApiResponse.ok(reindexService.status(tenantId));
    }
}
