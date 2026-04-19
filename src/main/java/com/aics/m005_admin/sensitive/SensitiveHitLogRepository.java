package com.aics.m005_admin.sensitive;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface SensitiveHitLogRepository extends JpaRepository<SensitiveHitLog, Long> {

    Page<SensitiveHitLog> findByTenantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long tenantId, OffsetDateTime from, Pageable pageable);

    long countByTenantIdAndCreatedAtGreaterThanEqual(Long tenantId, OffsetDateTime from);
}
