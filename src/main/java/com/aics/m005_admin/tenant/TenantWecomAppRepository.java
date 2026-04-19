package com.aics.m005_admin.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantWecomAppRepository extends JpaRepository<TenantWecomApp, Long> {

    List<TenantWecomApp> findByTenantId(Long tenantId);

    List<TenantWecomApp> findByTenantIdAndEnabledTrue(Long tenantId);

    Optional<TenantWecomApp> findByCorpIdAndAgentId(String corpId, Integer agentId);

    Optional<TenantWecomApp> findByCsAgentId(Long csAgentId);

    List<TenantWecomApp> findByTenantIdOrderByIdAsc(Long tenantId);

    long countByTenantId(Long tenantId);
}
