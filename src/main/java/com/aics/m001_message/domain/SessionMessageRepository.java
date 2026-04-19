package com.aics.m001_message.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface SessionMessageRepository extends JpaRepository<SessionMessage, Long> {

    Page<SessionMessage> findByTenantIdAndGroupIdOrderByCreatedAtDesc(Long tenantId, String groupId, Pageable p);

    Page<SessionMessage> findByTenantIdAndGroupIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long tenantId, String groupId, OffsetDateTime from, OffsetDateTime to, Pageable p);
}
