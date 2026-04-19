package com.aics.m003_kb.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {

    Page<KbDocument> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(Long tenantId, Pageable p);

    Page<KbDocument> findByTenantIdAndDeletedFalseAndStatusOrderByCreatedAtDesc(
            Long tenantId, String status, Pageable p);

    Optional<KbDocument> findByTenantIdAndFileHashAndDeletedFalse(Long tenantId, String hash);

    Optional<KbDocument> findByIdAndTenantId(Long id, Long tenantId);

    long countByTenantIdAndDeletedFalse(Long tenantId);

    List<KbDocument> findByTenantIdAndIdInAndDeletedFalse(Long tenantId, Collection<Long> ids);

    /** 按 tags 集合取交集；等价于 JSONB `?|` 操作符，但改用 jsonb_exists_any 避免与 JPA 位置参数冲突。*/
    @Query(value = "SELECT * FROM kb_document d "
            + "WHERE d.tenant_id = :tenantId AND d.deleted = false "
            + "AND jsonb_exists_any(d.tags, CAST(:tags AS text[]))",
            nativeQuery = true)
    List<KbDocument> findByTenantIdAndAnyTag(@Param("tenantId") Long tenantId,
                                             @Param("tags") String[] tags);
}
