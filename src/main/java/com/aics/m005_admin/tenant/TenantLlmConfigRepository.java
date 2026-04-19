package com.aics.m005_admin.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantLlmConfigRepository extends JpaRepository<TenantLlmConfig, Long> {

    List<TenantLlmConfig> findByTenantIdOrderByIdDesc(Long tenantId);

    List<TenantLlmConfig> findByTenantIdAndPurpose(Long tenantId, String purpose);

    Optional<TenantLlmConfig> findByTenantIdAndPurposeAndIsDefaultTrueAndEnabledTrue(Long tenantId, String purpose);

    Optional<TenantLlmConfig> findByIdAndTenantId(Long id, Long tenantId);
}
