package com.aics.m005_admin.tenant;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "tenant_llm_config")
public class TenantLlmConfig {

    public static final String PURPOSE_CHAT = "chat";
    public static final String PURPOSE_EMBEDDING = "embedding";
    public static final String PURPOSE_RERANK = "rerank";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String purpose;

    @Column(name = "api_key_cipher", nullable = false)
    private String apiKeyCipher;

    @Column(name = "api_key_tail")
    private String apiKeyTail;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(nullable = false)
    private String model;

    @Column(name = "embedding_dim")
    private Integer embeddingDim;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> params = new HashMap<>();

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_test_at")
    private OffsetDateTime lastTestAt;

    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    @Column(name = "last_test_msg")
    private String lastTestMsg;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = OffsetDateTime.now();
    }
}
