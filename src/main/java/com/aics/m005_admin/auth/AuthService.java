package com.aics.m005_admin.auth;

import com.aics.common.BizException;
import com.aics.m005_admin.tenant.Tenant;
import com.aics.m005_admin.tenant.TenantRepository;
import com.aics.m005_admin.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserRepository userRepo;
    private final UserGroupScopeRepository scopeRepo;
    private final TenantRepository tenantRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public Map<String, Object> login(String tenantCode, String username, String rawPassword) {
        Tenant tenant = resolveTenantForLogin(tenantCode, username);

        String status = tenant.getStatus();
        if (!"active".equals(status) && !"trial".equals(status)) {
            throw BizException.forbidden("租户已停用");
        }

        AdminUser u = userRepo.findByTenantIdAndUsername(tenant.getId(), username)
                .orElseThrow(() -> BizException.of("账号或密码错误"));
        if (!Boolean.TRUE.equals(u.getEnabled())) throw BizException.forbidden("账号已禁用");
        if (!encoder.matches(rawPassword, u.getPasswordHash())) {
            throw BizException.of("账号或密码错误");
        }
        u.setLastLoginAt(OffsetDateTime.now());
        userRepo.save(u);

        List<String> roleNames = u.getRoles().stream().map(AdminRole::getName).toList();
        String access = jwt.issueAccess(u.getId(), u.getTenantId(), tenant.getCode(), u.getUsername(), roleNames);
        String refresh = jwt.issueRefresh(u.getId(), u.getTenantId(), tenant.getCode(), u.getUsername());

        return Map.of(
                "accessToken", access,
                "refreshToken", refresh,
                "tenant", Map.of("id", tenant.getId(), "code", tenant.getCode(), "name", tenant.getName()),
                "user", toBrief(u, tenant));
    }

    /**
     * 登录时的租户解析策略：
     * <ol>
     *   <li>若前端显式传入 tenantCode → 直接按 code 查；查不到 400。</li>
     *   <li>未传 tenantCode → 全局按 username 查启用态账号：
     *     <ul>
     *       <li>0 条：回退到 default 租户，把"账号不存在"统一成"账号或密码错误"。</li>
     *       <li>1 条：允许单租户无歧义登录（兼容默认 admin / 单租户客户）。</li>
     *       <li>≥2 条：拒绝并提示输入 tenantCode，避免错签 JWT。</li>
     *     </ul>
     *   </li>
     * </ol>
     */
    private Tenant resolveTenantForLogin(String tenantCode, String username) {
        if (tenantCode != null && !tenantCode.isBlank()) {
            return tenantRepo.findByCode(tenantCode.trim())
                    .orElseThrow(() -> BizException.of("租户不存在"));
        }
        List<AdminUser> matches = userRepo.findByUsername(username).stream()
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .toList();
        if (matches.size() >= 2) {
            throw new BizException(400, "该账号存在于多个租户，请输入租户标识");
        }
        if (matches.size() == 1) {
            Long tid = matches.get(0).getTenantId();
            return tenantRepo.findById(tid).orElseThrow(() -> BizException.of("租户不存在"));
        }
        return tenantRepo.findByCode("default")
                .orElseThrow(() -> BizException.of("账号或密码错误"));
    }

    public Map<String, Object> refresh(String refreshToken) {
        Map<String, Object> claims = jwt.decode(refreshToken);
        if (!"refresh".equals(claims.get("type"))) throw BizException.of("非法的 refresh token");

        Long userId = (Long) claims.get("userId");
        AdminUser u = userRepo.findById(userId).orElseThrow(() -> BizException.of("用户不存在"));
        if (!Boolean.TRUE.equals(u.getEnabled())) throw BizException.forbidden("账号已禁用");

        Tenant tenant = tenantRepo.findById(u.getTenantId())
                .orElseThrow(() -> BizException.of("租户不存在"));

        List<String> roleNames = u.getRoles().stream().map(AdminRole::getName).toList();
        String access = jwt.issueAccess(u.getId(), u.getTenantId(), tenant.getCode(), u.getUsername(), roleNames);
        return Map.of("accessToken", access);
    }

    /** 首次登录强制改密 / 日常改密；两种场景共用，by 登录态用户改自己的密码。 */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw BizException.of("新密码至少 8 位");
        }
        boolean hasLetter = newPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = newPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw BizException.of("新密码需同时包含字母与数字");
        }

        AdminUser u = userRepo.findById(userId).orElseThrow(() -> BizException.of("用户不存在"));
        if (!encoder.matches(oldPassword, u.getPasswordHash())) {
            throw BizException.of("原密码错误");
        }
        if (encoder.matches(newPassword, u.getPasswordHash())) {
            throw BizException.of("新密码不能与旧密码相同");
        }
        u.setPasswordHash(encoder.encode(newPassword));
        u.setMustChangePassword(false);
        userRepo.save(u);
    }

    /** 从 JWT 构造 AdminPrincipal（含权限码与群数据域）。*/
    public AdminPrincipal loadPrincipal(Long userId) {
        AdminUser u = userRepo.findById(userId).orElse(null);
        if (u == null || !Boolean.TRUE.equals(u.getEnabled())) return null;
        return buildPrincipal(u);
    }

    public AdminPrincipal buildPrincipal(AdminUser u) {
        Set<String> authorities = new HashSet<>();
        List<String> roles = u.getRoles().stream().map(AdminRole::getName).toList();
        for (AdminRole r : u.getRoles()) {
            for (AdminPermission p : r.getPermissions()) {
                authorities.add(p.getCode());
            }
        }
        Set<String> scopes = scopeRepo.findByTenantIdAndUserId(u.getTenantId(), u.getId()).stream()
                .map(UserGroupScope::getGroupId)
                .collect(Collectors.toSet());
        return new AdminPrincipal(u.getId(), u.getTenantId(), u.getUsername(),
                u.getDisplayName(), roles, authorities, scopes);
    }

    private Map<String, Object> toBrief(AdminUser u, Tenant tenant) {
        List<String> roles = u.getRoles().stream().map(AdminRole::getName).collect(Collectors.toList());
        Set<String> perms = new HashSet<>();
        u.getRoles().forEach(r -> r.getPermissions().forEach(p -> perms.add(p.getCode())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("tenantId", u.getTenantId());
        m.put("tenantCode", tenant.getCode());
        m.put("tenantName", tenant.getName());
        m.put("displayName", u.getDisplayName() == null ? "" : u.getDisplayName());
        m.put("email", u.getEmail() == null ? "" : u.getEmail());
        m.put("roles", roles);
        m.put("permissions", perms);
        m.put("mustChangePassword", Boolean.TRUE.equals(u.getMustChangePassword()));
        return m;
    }
}
