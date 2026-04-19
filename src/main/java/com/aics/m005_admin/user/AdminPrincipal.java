package com.aics.m005_admin.user;

import java.util.List;
import java.util.Set;

/**
 * JWT 认证后放入 SecurityContext 的主体。用于控制器 @AuthenticationPrincipal 注入。
 */
public record AdminPrincipal(
        Long id,
        Long tenantId,
        String username,
        String displayName,
        List<String> roles,
        Set<String> authorities,
        Set<String> groupScope
) {
    public boolean hasAuthority(String code) {
        return authorities != null && authorities.contains(code);
    }

    public boolean canAccessGroup(String groupId) {
        return groupScope == null || groupScope.isEmpty() || groupScope.contains(groupId);
    }
}
