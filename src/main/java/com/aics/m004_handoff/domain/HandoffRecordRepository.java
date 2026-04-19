package com.aics.m004_handoff.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface HandoffRecordRepository extends JpaRepository<HandoffRecord, Long> {

    Optional<HandoffRecord> findFirstByTenantIdAndGroupIdAndClosedAtIsNullOrderByTriggeredAtDesc(
            Long tenantId, String groupId);

    Page<HandoffRecord> findByTenantIdAndGroupIdOrderByTriggeredAtDesc(
            Long tenantId, String groupId, Pageable p);

    @Query("select h from HandoffRecord h where h.closedAt is null and h.triggeredAt < :threshold")
    List<HandoffRecord> findStale(OffsetDateTime threshold);

    @Modifying
    @Query("update HandoffRecord h set h.closedAt = :now, h.closeReason = :reason " +
            "where h.id = :id and h.closedAt is null")
    int close(Long id, OffsetDateTime now, String reason);
}
