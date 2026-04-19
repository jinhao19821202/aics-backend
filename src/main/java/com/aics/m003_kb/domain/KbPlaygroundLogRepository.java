package com.aics.m003_kb.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KbPlaygroundLogRepository extends JpaRepository<KbPlaygroundLog, Long> {

    List<KbPlaygroundLog> findByTenantIdAndTopDocIdOrderByCreatedAtDesc(
            Long tenantId, Long topDocId, Pageable pageable);
}
