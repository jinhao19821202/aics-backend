package com.aics.m004_handoff.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "handoff_record")
public class HandoffRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "trigger_reason", nullable = false)
    private String triggerReason;

    @Type(JsonType.class)
    @Column(name = "trigger_payload", columnDefinition = "jsonb")
    private Map<String, Object> triggerPayload;

    @Column(name = "triggered_at", insertable = false, updatable = false)
    private OffsetDateTime triggeredAt;

    @Column(name = "agent_userid")
    private String agentUserid;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "close_reason")
    private String closeReason;
}
