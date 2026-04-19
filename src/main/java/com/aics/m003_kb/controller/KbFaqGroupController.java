package com.aics.m003_kb.controller;

import com.aics.common.ApiResponse;
import com.aics.m003_kb.service.KbFaqGroupDto;
import com.aics.m003_kb.service.KbFaqGroupService;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb/faq-groups")
@RequiredArgsConstructor
public class KbFaqGroupController {

    private final KbFaqGroupService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('kb:faq:read','kb:faq:write','kb:faq:group:manage')")
    public ApiResponse<List<KbFaqGroupDto.Response>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('kb:faq:read','kb:faq:write','kb:faq:group:manage')")
    public ApiResponse<KbFaqGroupDto.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('kb:faq:group:manage')")
    public ApiResponse<KbFaqGroupDto.Response> create(@RequestBody KbFaqGroupDto.Request req,
                                                       @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.create(req, op));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('kb:faq:group:manage')")
    public ApiResponse<KbFaqGroupDto.Response> update(@PathVariable Long id,
                                                       @RequestBody KbFaqGroupDto.Request req,
                                                       @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.update(id, req, op));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('kb:faq:group:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal op) {
        service.delete(id, op);
        return ApiResponse.ok();
    }
}
