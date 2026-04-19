package com.aics.m005_admin.wecom;

import com.aics.common.ApiResponse;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * P004-A F001：租户企微应用自助管理。
 * 权限 wecom:app:manage 由 TENANT_OWNER 默认持有；运营只读由 ops 模块另行提供。
 */
@RestController
@RequestMapping("/api/admin/wecom-apps")
@RequiredArgsConstructor
public class TenantWecomAppController {

    private final TenantWecomAppService service;

    @GetMapping
    @PreAuthorize("hasAuthority('wecom:app:manage')")
    public ApiResponse<List<TenantWecomAppDto.Response>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('wecom:app:manage')")
    public ApiResponse<TenantWecomAppDto.Response> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('wecom:app:manage')")
    public ApiResponse<TenantWecomAppDto.Response> create(@RequestBody TenantWecomAppDto.Request req,
                                                          @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.create(req, op));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('wecom:app:manage')")
    public ApiResponse<TenantWecomAppDto.Response> update(@PathVariable Long id,
                                                          @RequestBody TenantWecomAppDto.Request req,
                                                          @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.update(id, req, op));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('wecom:app:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal op) {
        service.delete(id, op);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('wecom:app:manage')")
    public ApiResponse<TenantWecomAppDto.TestResult> test(@PathVariable Long id,
                                                          @AuthenticationPrincipal AdminPrincipal op) {
        return ApiResponse.ok(service.test(id, op));
    }

    @GetMapping("/{id}/callback-url")
    @PreAuthorize("hasAuthority('wecom:app:manage')")
    public ApiResponse<TenantWecomAppDto.CallbackUrlResponse> callbackUrl(@PathVariable Long id) {
        return ApiResponse.ok(service.callbackUrl(id));
    }
}
