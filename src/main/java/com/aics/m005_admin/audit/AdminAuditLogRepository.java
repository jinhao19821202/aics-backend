package com.aics.m005_admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    @Query("select a from AdminAuditLog a where " +
            "a.tenantId = :tenantId and " +
            "(cast(:adminId as long) is null or a.adminId = :adminId) and " +
            "(cast(:action as string) is null or a.action = :action) and " +
            "(cast(:from as timestamp) is null or a.createdAt >= :from) " +
            "order by a.createdAt desc")
    Page<AdminAuditLog> search(@Param("tenantId") Long tenantId,
                               @Param("adminId") Long adminId,
                               @Param("action") String action,
                               @Param("from") OffsetDateTime from,
                               Pageable pageable);
}
