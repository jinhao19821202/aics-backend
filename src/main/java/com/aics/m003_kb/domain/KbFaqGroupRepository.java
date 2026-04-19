package com.aics.m003_kb.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KbFaqGroupRepository extends JpaRepository<KbFaqGroup, Long> {

    List<KbFaqGroup> findByTenantIdOrderBySortOrderAscIdAsc(Long tenantId);

    Optional<KbFaqGroup> findByTenantIdAndName(Long tenantId, String name);

    boolean existsByTenantIdAndName(Long tenantId, String name);

    Optional<KbFaqGroup> findByIdAndTenantId(Long id, Long tenantId);
}
