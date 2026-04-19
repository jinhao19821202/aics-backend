package com.aics.m005_admin.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantCsAgentRepository extends JpaRepository<TenantCsAgent, Long> {

    List<TenantCsAgent> findByTenantIdOrderByIdDesc(Long tenantId);

    List<TenantCsAgent> findByTenantIdAndEnabledTrue(Long tenantId);

    Optional<TenantCsAgent> findByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    long countByTenantId(Long tenantId);
}
