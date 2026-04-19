package com.aics.m003_kb.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KbFaqRepository extends JpaRepository<KbFaq, Long> {

    List<KbFaq> findByTenantIdAndEnabledTrue(Long tenantId);

    Page<KbFaq> findByTenantIdAndQuestionContainingIgnoreCaseOrderByUpdatedAtDesc(
            Long tenantId, String q, Pageable p);

    Page<KbFaq> findByTenantIdOrderByUpdatedAtDesc(Long tenantId, Pageable p);

    Optional<KbFaq> findByIdAndTenantId(Long id, Long tenantId);

    /** P004-B F003：按分组列查询。 */
    Page<KbFaq> findByTenantIdAndGroupIdOrderByUpdatedAtDesc(Long tenantId, Long groupId, Pageable p);

    Page<KbFaq> findByTenantIdAndGroupIdAndQuestionContainingIgnoreCaseOrderByUpdatedAtDesc(
            Long tenantId, Long groupId, String q, Pageable p);

    /** P004-B F003：统计某分组下的 FAQ 条数。 */
    long countByGroupId(Long groupId);

    @Query(value = "SELECT COUNT(*) FROM kb_faq WHERE tenant_id = :tenantId AND group_id = :groupId", nativeQuery = true)
    long countByTenantIdAndGroupId(@Param("tenantId") Long tenantId, @Param("groupId") Long groupId);
}
