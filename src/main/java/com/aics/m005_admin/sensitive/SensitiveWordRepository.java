package com.aics.m005_admin.sensitive;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {

    List<SensitiveWord> findByTenantIdAndEnabledTrue(Long tenantId);

    Page<SensitiveWord> findByTenantIdAndWordContainingIgnoreCaseOrderByIdDesc(Long tenantId, String w, Pageable pageable);

    Page<SensitiveWord> findByTenantIdOrderByIdDesc(Long tenantId, Pageable pageable);

    Optional<SensitiveWord> findByIdAndTenantId(Long id, Long tenantId);
}
