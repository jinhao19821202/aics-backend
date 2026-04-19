package com.aics.m005_admin.csagent;

import com.aics.common.ApiResponse;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P004-A F002：租户智能客服管理。
 * 权限：读用 cs:agent:read，写/绑定用 cs:agent:manage（TENANT_OWNER 默认持有）。
 */
@RestController
@RequestMapping("/api/admin/cs-agents")
@RequiredArgsConstructor
public class TenantCsAgentController {

    private final TenantCsAgentService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('cs:agent:read','cs:agent:manage')")
    public ApiResponse<List<TenantCsAgentDto.Response>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('cs:agent:read','cs:agent:manage')")
    public ApiResponse<TenantCsAgentDto.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('cs:agent:manage')")
    public ApiResponse<TenantCsAgentDto.Response> create(@RequestBody TenantCsAgentDto.Request req,
                                                         @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.create(req, op));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('cs:agent:manage')")
    public ApiResponse<TenantCsAgentDto.Response> update(@PathVariable Long id,
                                                         @RequestBody TenantCsAgentDto.Request req,
                                                         @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.update(id, req, op));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('cs:agent:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal op) {
        service.delete(id, op);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/bindings")
    @PreAuthorize("hasAuthority('cs:agent:manage')")
    public ApiResponse<TenantCsAgentDto.Response> bind(@PathVariable Long id,
                                                       @RequestBody TenantCsAgentDto.BindRequest req,
                                                       @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.bindWecomApp(id, req.getWecomAppId(), op));
    }

    @DeleteMapping("/{id}/bindings/{wecomAppId}")
    @PreAuthorize("hasAuthority('cs:agent:manage')")
    public ApiResponse<TenantCsAgentDto.Response> unbind(@PathVariable Long id,
                                                         @PathVariable Long wecomAppId,
                                                         @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.unbindWecomApp(id, wecomAppId, op));
    }
}
