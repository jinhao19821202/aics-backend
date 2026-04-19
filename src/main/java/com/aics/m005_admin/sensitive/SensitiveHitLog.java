package com.aics.m005_admin.sensitive;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "sensitive_hit_log")
public class SensitiveHitLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String direction;   // INBOUND/OUTBOUND

    @Column(name = "group_id")
    private String groupId;

    @Column(nullable = false)
    private String word;

    @Column(nullable = false)
    private String level;

    @Column(name = "action_taken", nullable = false)
    private String actionTaken;

    @Column(name = "original_snippet")
    private String originalSnippet;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
