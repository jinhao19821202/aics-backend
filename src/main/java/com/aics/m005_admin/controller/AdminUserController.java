package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.common.BizException;
import com.aics.common.PageResult;
import com.aics.common.tenant.TenantContext;
import com.aics.m005_admin.audit.AdminAuditLogger;
import com.aics.m005_admin.user.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserRepository userRepo;
    private final AdminRoleRepository roleRepo;
    private final UserGroupScopeRepository scopeRepo;
    private final PasswordEncoder encoder;
    private final AdminAuditLogger audit;

    @GetMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<PageResult<Map<String, Object>>> list(@RequestParam(required = false) String keyword,
                                                             @RequestParam(defaultValue = "1") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        Long tenantId = TenantContext.require();
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size);
        Page<AdminUser> pg = (keyword == null || keyword.isBlank())
                ? userRepo.findByTenantIdOrderByIdDesc(tenantId, pr)
                : userRepo.findByTenantIdAndUsernameContainingIgnoreCaseOrTenantIdAndDisplayNameContainingIgnoreCaseOrderByIdDesc(
                        tenantId, keyword, tenantId, keyword, pr);
        return ApiResponse.ok(PageResult.of(pg.map(this::brief)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public ApiResponse<Map<String, Object>> create(@RequestBody UpsertReq req,
                                                   @AuthenticationPrincipal AdminPrincipal op) {
        if (req.getUsername() == null || req.getPassword() == null) throw BizException.of("用户名和密码必填");
        Long tenantId = TenantContext.require();
        if (userRepo.findByTenantIdAndUsername(tenantId, req.getUsername()).isPresent())
            throw BizException.conflict("用户名已存在");

        AdminUser u = new AdminUser();
        u.setTenantId(tenantId);
        u.setUsername(req.getUsername());
        u.setPasswordHash(encoder.encode(req.getPassword()));
        u.setDisplayName(req.getDisplayName());
        u.setEmail(req.getEmail());
        u.setEnabled(req.getEnabled() == null ? true : req.getEnabled());
        u.setRoles(new HashSet<>(roleRepo.findByIdIn(req.getRoleIds() == null ? List.of() : req.getRoleIds())));
        u.setUpdatedAt(OffsetDateTime.now());
        u = userRepo.save(u);
        if (req.getGroupScope() != null) replaceGroupScope(tenantId, u.getId(), req.getGroupScope());
        audit.record(op, "user.create", "admin_user", String.valueOf(u.getId()), null, Map.of("username", u.getUsername()));
        return ApiResponse.ok(brief(u));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody UpsertReq req,
                                                   @AuthenticationPrincipal AdminPrincipal op) {
        Long tenantId = TenantContext.require();
        AdminUser u = userRepo.findById(id)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        Map<String, Object> before = Map.of("displayName", u.getDisplayName() == null ? "" : u.getDisplayName(),
                "enabled", u.getEnabled(),
                "roles", u.getRoles().stream().map(AdminRole::getId).toList());
        if (req.getDisplayName() != null) u.setDisplayName(req.getDisplayName());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getEnabled() != null) u.setEnabled(req.getEnabled());
        if (req.getRoleIds() != null) u.setRoles(new HashSet<>(roleRepo.findByIdIn(req.getRoleIds())));
        if (req.getPassword() != null && !req.getPassword().isBlank()) u.setPasswordHash(encoder.encode(req.getPassword()));
        u.setUpdatedAt(OffsetDateTime.now());
        u = userRepo.save(u);
        if (req.getGroupScope() != null) replaceGroupScope(tenantId, u.getId(), req.getGroupScope());
        audit.record(op, "user.update", "admin_user", String.valueOf(id), before,
                Map.of("displayName", u.getDisplayName() == null ? "" : u.getDisplayName(),
                        "enabled", u.getEnabled(),
                        "roles", u.getRoles().stream().map(AdminRole::getId).toList()));
        return ApiResponse.ok(brief(u));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AdminPrincipal op) {
        if (op != null && op.id().equals(id)) throw BizException.of("不能删除自己");
        Long tenantId = TenantContext.require();
        userRepo.findById(id)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .ifPresent(u -> {
                    userRepo.delete(u);
                    scopeRepo.deleteByTenantIdAndUserId(tenantId, id);
                    audit.record(op, "user.delete", "admin_user", String.valueOf(id), Map.of("username", u.getUsername()), null);
                });
        return ApiResponse.ok();
    }

    private void replaceGroupScope(Long tenantId, Long userId, List<String> groupIds) {
        scopeRepo.deleteByTenantIdAndUserId(tenantId, userId);
        Set<String> dedup = new HashSet<>(groupIds);
        List<UserGroupScope> items = dedup.stream()
                .map(g -> {
                    UserGroupScope s = new UserGroupScope();
                    s.setTenantId(tenantId);
                    s.setUserId(userId);
                    s.setGroupId(g);
                    return s;
                })
                .collect(Collectors.toList());
        scopeRepo.saveAll(items);
    }

    private Map<String, Object> brief(AdminUser u) {
        Long tenantId = TenantContext.require();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("displayName", u.getDisplayName() == null ? "" : u.getDisplayName());
        m.put("email", u.getEmail() == null ? "" : u.getEmail());
        m.put("enabled", u.getEnabled());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("roles", u.getRoles().stream()
                .map(r -> Map.of("id", r.getId(), "name", r.getName())).toList());
        m.put("groupScope", scopeRepo.findByTenantIdAndUserId(tenantId, u.getId()).stream()
                .map(UserGroupScope::getGroupId).toList());
        return m;
    }

    @Data
    public static class UpsertReq {
        private String username;
        private String password;
        private String displayName;
        private String email;
        private Boolean enabled;
        private List<Long> roleIds;
        private List<String> groupScope;
    }
}
