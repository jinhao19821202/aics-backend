package com.aics.m005_admin.tenant;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status = "active";

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;

    @Column(nullable = false)
    private String plan = "basic";

    @Column(name = "quota_kb_docs", nullable = false)
    private Integer quotaKbDocs = 100;

    @Column(name = "quota_monthly_tokens", nullable = false)
    private Long quotaMonthlyTokens = 10_000_000L;

    @Column(name = "milvus_collection")
    private String milvusCollection;

    @Column(name = "embedding_dim")
    private Integer embeddingDim;

    @Column(name = "reindex_status", nullable = false)
    private String reindexStatus = "idle";

    @Column(name = "reindex_last_error")
    private String reindexLastError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
