package com.aics.m004_handoff.controller;

import com.aics.common.ApiResponse;
import com.aics.common.PageResult;
import com.aics.common.tenant.TenantContext;
import com.aics.m004_handoff.domain.HandoffRecord;
import com.aics.m004_handoff.domain.HandoffRecordRepository;
import com.aics.m004_handoff.service.HandoffService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/handoff")
@RequiredArgsConstructor
public class HandoffController {

    private final HandoffRecordRepository repo;
    private final HandoffService service;

    @GetMapping
    @PreAuthorize("hasAuthority('handoff:manage') or hasAuthority('audit:session:read')")
    public ApiResponse<PageResult<HandoffRecord>> list(@RequestParam(required = false) String groupId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        Long tenantId = TenantContext.require();
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size);
        Page<HandoffRecord> pg = groupId == null
                ? repo.findAll(pr)
                : repo.findByTenantIdAndGroupIdOrderByTriggeredAtDesc(tenantId, groupId, pr);
        return ApiResponse.ok(PageResult.of(pg));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('handoff:manage')")
    public ApiResponse<Void> close(@PathVariable Long id,
                                   @RequestParam(defaultValue = "MANUAL") String reason) {
        service.close(id, reason);
        return ApiResponse.ok();
    }
}
