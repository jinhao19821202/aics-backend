package com.aics.m005_admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface LlmInvocationRepository extends JpaRepository<LlmInvocation, Long> {

    Page<LlmInvocation> findByTenantIdAndGroupIdOrderByCreatedAtDesc(Long tenantId, String groupId, Pageable pageable);

    Page<LlmInvocation> findByTenantIdAndConversationIdOrderByCreatedAtAsc(Long tenantId, String conversationId, Pageable pageable);

    @Query("select l from LlmInvocation l where " +
            "l.tenantId = :tenantId and " +
            "(cast(:groupId as string) is null or l.groupId = :groupId) and " +
            "(cast(:from as timestamp) is null or l.createdAt >= :from) and " +
            "(cast(:to as timestamp) is null or l.createdAt < :to) and " +
            "(cast(:status as string) is null or l.status = :status) " +
            "order by l.createdAt desc")
    Page<LlmInvocation> search(@Param("tenantId") Long tenantId,
                               @Param("groupId") String groupId,
                               @Param("from") OffsetDateTime from,
                               @Param("to") OffsetDateTime to,
                               @Param("status") String status,
                               Pageable pageable);

    @Query("select count(l) from LlmInvocation l where l.tenantId = :tenantId and l.createdAt >= :from and l.createdAt < :to")
    long countBetween(@Param("tenantId") Long tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("select count(l) from LlmInvocation l where l.tenantId = :tenantId and l.handoff = true and l.createdAt >= :from and l.createdAt < :to")
    long countHandoffBetween(@Param("tenantId") Long tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("select coalesce(avg(l.latencyMs),0) from LlmInvocation l where l.tenantId = :tenantId and l.createdAt >= :from and l.createdAt < :to")
    Double avgLatencyBetween(@Param("tenantId") Long tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "select coalesce(percentile_cont(0.95) within group (order by latency_ms), 0) " +
            "from llm_invocation where tenant_id = :tenantId and created_at >= :from and created_at < :to", nativeQuery = true)
    Double p95LatencyBetween(@Param("tenantId") Long tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("select coalesce(sum(l.promptTokens),0) + coalesce(sum(l.completionTokens),0) " +
            "from LlmInvocation l where l.tenantId = :tenantId and l.createdAt >= :from and l.createdAt < :to")
    Long totalTokensBetween(@Param("tenantId") Long tenantId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    List<LlmInvocation> findTop20ByTenantIdOrderByCreatedAtDesc(Long tenantId);

    /**
     * 跨租户用量聚合（运营 billing）。
     * 返回 Object[] {tenantId:Long, callCount:Long, promptTokens:Long, completionTokens:Long,
     * handoffCount:Long, avgLatencyMs:Double}。
     */
    @Query("select l.tenantId, count(l), " +
            "coalesce(sum(l.promptTokens),0), " +
            "coalesce(sum(l.completionTokens),0), " +
            "coalesce(sum(case when l.handoff = true then 1 else 0 end),0), " +
            "coalesce(avg(l.latencyMs),0) " +
            "from LlmInvocation l " +
            "where l.createdAt >= :from and l.createdAt < :to " +
            "and (cast(:tenantId as long) is null or l.tenantId = :tenantId) " +
            "group by l.tenantId")
    List<Object[]> aggregateByTenant(@Param("from") OffsetDateTime from,
                                     @Param("to") OffsetDateTime to,
                                     @Param("tenantId") Long tenantId);

    /** 某租户按 model 维度的用量细分。返回 {model:String, callCount:Long, tokens:Long}。*/
    @Query("select l.model, count(l), " +
            "coalesce(sum(l.promptTokens),0) + coalesce(sum(l.completionTokens),0) " +
            "from LlmInvocation l " +
            "where l.tenantId = :tenantId and l.createdAt >= :from and l.createdAt < :to " +
            "group by l.model order by count(l) desc")
    List<Object[]> breakdownByModel(@Param("tenantId") Long tenantId,
                                    @Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to);
}
