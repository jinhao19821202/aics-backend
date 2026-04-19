package com.aics.m003_kb.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface KbChunkRepository extends JpaRepository<KbChunk, Long> {

    List<KbChunk> findByTenantIdAndDocId(Long tenantId, Long docId);

    void deleteByTenantIdAndDocId(Long tenantId, Long docId);

    List<KbChunk> findByTenantIdAndMilvusIdIn(Long tenantId, Collection<Long> milvusIds);

    Page<KbChunk> findByTenantIdAndEnabledTrueOrderByIdAsc(Long tenantId, Pageable pageable);

    Page<KbChunk> findByTenantIdAndDocIdOrderByIdAsc(Long tenantId, Long docId, Pageable pageable);

    /** 取某 chunk 所在文档内 id 紧邻的前/后一个 chunk（用于调试页定位显示上下文）。 */
    List<KbChunk> findFirst1ByTenantIdAndDocIdAndIdLessThanOrderByIdDesc(Long tenantId, Long docId, Long id);

    List<KbChunk> findFirst1ByTenantIdAndDocIdAndIdGreaterThanOrderByIdAsc(Long tenantId, Long docId, Long id);
}
