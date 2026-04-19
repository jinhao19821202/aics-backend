package com.aics.m003_kb.controller;

import com.aics.common.ApiResponse;
import com.aics.common.PageResult;
import com.aics.common.tenant.TenantContext;
import com.aics.m003_kb.domain.KbFaq;
import com.aics.m003_kb.domain.KbFaqRepository;
import com.aics.m003_kb.service.FaqService;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kb/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService service;
    private final KbFaqRepository repo;

    @GetMapping
    @PreAuthorize("hasAuthority('kb:faq:read')")
    public ApiResponse<PageResult<KbFaq>> list(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long groupId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        Long tenantId = TenantContext.require();
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size);
        boolean hasKw = keyword != null && !keyword.isBlank();
        Page<KbFaq> pg;
        if (groupId != null) {
            pg = hasKw
                    ? repo.findByTenantIdAndGroupIdAndQuestionContainingIgnoreCaseOrderByUpdatedAtDesc(tenantId, groupId, keyword, pr)
                    : repo.findByTenantIdAndGroupIdOrderByUpdatedAtDesc(tenantId, groupId, pr);
        } else {
            pg = hasKw
                    ? repo.findByTenantIdAndQuestionContainingIgnoreCaseOrderByUpdatedAtDesc(tenantId, keyword, pr)
                    : repo.findByTenantIdOrderByUpdatedAtDesc(tenantId, pr);
        }
        return ApiResponse.ok(PageResult.of(pg));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('kb:faq:write')")
    public ApiResponse<KbFaq> create(@RequestBody KbFaq faq, @AuthenticationPrincipal AdminPrincipal admin) {
        return ApiResponse.ok(service.create(faq, admin == null ? null : admin.id()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('kb:faq:write')")
    public ApiResponse<KbFaq> update(@PathVariable Long id, @RequestBody KbFaq faq) {
        return ApiResponse.ok(service.update(id, faq));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('kb:faq:write')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasAuthority('kb:faq:write')")
    public ApiResponse<FaqService.ReindexResult> reindex() {
        return ApiResponse.ok(service.reindexAll());
    }
}
