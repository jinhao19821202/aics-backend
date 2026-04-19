package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.m005_admin.llm.LlmConfigDto;
import com.aics.m005_admin.llm.LlmConfigService;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/llm-configs")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmConfigService service;

    @GetMapping
    @PreAuthorize("hasAuthority('tenant:model:view') or hasAuthority('tenant:model:config')")
    public ApiResponse<List<LlmConfigDto.Response>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:model:view') or hasAuthority('tenant:model:config')")
    public ApiResponse<LlmConfigDto.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:model:config')")
    public ApiResponse<LlmConfigDto.Response> create(@RequestBody LlmConfigDto.Request req,
                                                     @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.create(req, op));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:model:config')")
    public ApiResponse<LlmConfigDto.Response> update(@PathVariable Long id,
                                                     @RequestBody LlmConfigDto.Request req,
                                                     @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.update(id, req, op));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:model:config')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal op) {
        service.delete(id, op);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('tenant:model:config')")
    public ApiResponse<LlmConfigDto.TestResult> test(@PathVariable Long id,
                                                     @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.test(id, op));
    }
}
