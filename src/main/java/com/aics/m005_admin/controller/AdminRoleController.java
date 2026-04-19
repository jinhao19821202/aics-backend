package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.common.BizException;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.user.AdminPermission;
import com.aics.m005_admin.user.AdminPermissionRepository;
import com.aics.m005_admin.user.AdminPrincipal;
import com.aics.m005_admin.user.AdminRole;
import com.aics.m005_admin.user.AdminRoleRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleRepository roleRepo;
    private final AdminPermissionRepository permRepo;
    private final AdminAuditLogger audit;

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(roleRepo.findAll().stream().map(this::brief).toList());
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<List<AdminPermission>> permissions() {
        return ApiResponse.ok(permRepo.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody UpsertReq req,
                                                   @AuthenticationPrincipal AdminPrincipal op) {
        if (req.getName() == null || req.getName().isBlank()) throw BizException.of("角色名必填");
        if (roleRepo.findByName(req.getName()).isPresent()) throw BizException.conflict("角色名已存在");
        AdminRole r = new AdminRole();
        r.setName(req.getName());
        r.setDescription(req.getDescription());
        r.setBuiltIn(false);
        if (req.getPermissionCodes() != null) {
            r.setPermissions(new HashSet<>(permRepo.findByCodeIn(req.getPermissionCodes())));
        }
        r = roleRepo.save(r);
        audit.record(op, "role.create", "admin_role", String.valueOf(r.getId()), null,
                Map.of("name", r.getName(), "perms", req.getPermissionCodes() == null ? List.of() : req.getPermissionCodes()));
        return ApiResponse.ok(brief(r));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody UpsertReq req,
                                                   @AuthenticationPrincipal AdminPrincipal op) {
        AdminRole r = roleRepo.findById(id).orElseThrow(() -> BizException.notFound("角色不存在"));
        if (Boolean.TRUE.equals(r.getBuiltIn())) throw BizException.forbidden("内置角色不可修改");
        Map<String, Object> before = Map.of("name", r.getName(),
                "perms", r.getPermissions().stream().map(AdminPermission::getCode).toList());
        if (req.getDescription() != null) r.setDescription(req.getDescription());
        if (req.getPermissionCodes() != null) {
            r.setPermissions(new HashSet<>(permRepo.findByCodeIn(req.getPermissionCodes())));
        }
        r = roleRepo.save(r);
        audit.record(op, "role.update", "admin_role", String.valueOf(id), before,
                Map.of("perms", r.getPermissions().stream().map(AdminPermission::getCode).toList()));
        return ApiResponse.ok(brief(r));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal op) {
        AdminRole r = roleRepo.findById(id).orElseThrow(() -> BizException.notFound("角色不存在"));
        if (Boolean.TRUE.equals(r.getBuiltIn())) throw BizException.forbidden("内置角色不可删除");
        roleRepo.delete(r);
        audit.record(op, "role.delete", "admin_role", String.valueOf(id), Map.of("name", r.getName()), null);
        return ApiResponse.ok();
    }

    private Map<String, Object> brief(AdminRole r) {
        return Map.of(
                "id", r.getId(),
                "name", r.getName(),
                "description", r.getDescription() == null ? "" : r.getDescription(),
                "builtIn", r.getBuiltIn(),
                "permissions", r.getPermissions().stream().map(AdminPermission::getCode).toList());
    }

    @Data
    public static class UpsertReq {
        private String name;
        private String description;
        private List<String> permissionCodes;
    }
}
