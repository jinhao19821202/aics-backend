package com.aics.m004_handoff.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "group_session")
public class GroupSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(nullable = false)
    private String state = "BOT_ACTIVE";

    @Column(name = "handoff_id")
    private Long handoffId;

    @Column(name = "last_activity_at")
    private OffsetDateTime lastActivityAt;

    @Version
    private Integer version;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
