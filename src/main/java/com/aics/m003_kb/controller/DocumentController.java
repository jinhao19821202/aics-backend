package com.aics.m003_kb.controller;

import com.aics.common.ApiResponse;
import com.aics.common.PageResult;
import com.aics.common.tenant.TenantContext;
import com.aics.m003_kb.domain.KbDocument;
import com.aics.m003_kb.domain.KbDocumentRepository;
import com.aics.m003_kb.service.KnowledgeIndexService;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/kb/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final KnowledgeIndexService indexer;
    private final KbDocumentRepository docRepo;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('kb:document:write')")
    public ApiResponse<KbDocument> upload(@RequestParam MultipartFile file,
                                          @RequestParam(required = false) String title,
                                          @RequestParam(required = false) String tags,
                                          @AuthenticationPrincipal AdminPrincipal admin) {
        List<String> tagList = tags == null || tags.isBlank() ? List.of() : Arrays.asList(tags.split(","));
        KbDocument doc = indexer.uploadDocument(file, title, tagList, admin == null ? null : admin.id());
        return ApiResponse.ok(doc);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('kb:document:read')")
    public ApiResponse<PageResult<KbDocument>> list(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Long tenantId = TenantContext.require();
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size);
        Page<KbDocument> pg = (status == null || status.isBlank())
                ? docRepo.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId, pr)
                : docRepo.findByTenantIdAndDeletedFalseAndStatusOrderByCreatedAtDesc(tenantId, status, pr);
        return ApiResponse.ok(PageResult.of(pg));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('kb:document:write')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        indexer.softDelete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('kb:document:write')")
    public ApiResponse<KbDocument> retry(@PathVariable Long id) {
        return ApiResponse.ok(indexer.retry(id));
    }
}
