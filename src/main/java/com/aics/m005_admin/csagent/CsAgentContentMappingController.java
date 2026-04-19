package com.aics.m005_admin.csagent;

import com.aics.common.ApiResponse;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P004-B F004：智能客服内容归属（Agent ↔ FAQ 分组 / 文档）。
 * 读：cs:agent:read，写：cs:agent:manage。
 */
@RestController
@RequestMapping("/api/admin/cs-agents/{id}")
@RequiredArgsConstructor
public class CsAgentContentMappingController {

    private final CsAgentContentMappingService service;

    @GetMapping("/faq-groups")
    @PreAuthorize("hasAnyAuthority('cs:agent:read','cs:agent:manage')")
    public ApiResponse<List<CsAgentContentMappingDto.FaqGroupItem>> listFaqGroups(@PathVariable Long id) {
        return ApiResponse.ok(service.listFaqGroups(id));
    }

    @PutMapping("/faq-groups")
    @PreAuthorize("hasAuthority('cs:agent:manage')")
    public ApiResponse<List<CsAgentContentMappingDto.FaqGroupItem>> replaceFaqGroups(
            @PathVariable Long id,
            @RequestBody CsAgentContentMappingDto.ReplaceRequest req,
            @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.replaceFaqGroups(id, req == null ? null : req.getIds(), op));
    }

    @GetMapping("/documents")
    @PreAuthorize("hasAnyAuthority('cs:agent:read','cs:agent:manage')")
    public ApiResponse<List<CsAgentContentMappingDto.DocumentItem>> listDocuments(@PathVariable Long id) {
        return ApiResponse.ok(service.listDocuments(id));
    }

    @PutMapping("/documents")
    @PreAuthorize("hasAuthority('cs:agent:manage')")
    public ApiResponse<List<CsAgentContentMappingDto.DocumentItem>> replaceDocuments(
            @PathVariable Long id,
            @RequestBody CsAgentContentMappingDto.ReplaceRequest req,
            @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.replaceDocuments(id, req == null ? null : req.getIds(), op));
    }
}
