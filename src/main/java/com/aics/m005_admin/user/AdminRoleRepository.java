package com.aics.m005_admin.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {
    Optional<AdminRole> findByName(String name);

    Optional<AdminRole> findByTenantIdAndName(Long tenantId, String name);

    List<AdminRole> findByIdIn(List<Long> ids);
}
