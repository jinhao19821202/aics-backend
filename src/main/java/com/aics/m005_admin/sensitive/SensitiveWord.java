package com.aics.m005_admin.sensitive;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "sensitive_word")
public class SensitiveWord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String word;

    @Column(nullable = false)
    private String category;   // POLITICS/PORN/VIOLENCE/PRIVACY/COMPETITOR/CUSTOM

    @Column(nullable = false)
    private String level;      // HIGH/MEDIUM/LOW

    @Column(nullable = false)
    private String action;     // BLOCK/MASK/ALERT

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
