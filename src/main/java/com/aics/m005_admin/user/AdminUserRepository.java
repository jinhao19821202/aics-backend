package com.aics.m005_admin.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByTenantIdAndUsername(Long tenantId, String username);

    /** 跨租户按 username 查询：用于登录时的歧义检测。启用状态可能包含禁用记录，调用方需自行过滤。 */
    List<AdminUser> findByUsername(String username);

    Page<AdminUser> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);

    Page<AdminUser> findByTenantIdAndUsernameContainingIgnoreCaseOrTenantIdAndDisplayNameContainingIgnoreCaseOrderByIdDesc(
            Long t1, String u, Long t2, String d, Pageable pageable);
}
