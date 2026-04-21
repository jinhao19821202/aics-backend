package com.aics.m001_message.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SessionMessageRepository extends JpaRepository<SessionMessage, Long> {

    Page<SessionMessage> findByTenantIdAndGroupIdOrderByCreatedAtDesc(Long tenantId, String groupId, Pageable p);

    Page<SessionMessage> findByTenantIdAndGroupIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long tenantId, String groupId, OffsetDateTime from, OffsetDateTime to, Pageable p);

    /** P005 F003 详情页：定位与 wecom_message 同 msg_id 的 user 角色 session_message 行。 */
    Optional<SessionMessage> findFirstByTenantIdAndMsgIdAndRole(Long tenantId, String msgId, String role);
}
