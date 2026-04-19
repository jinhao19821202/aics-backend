package com.aics.m003_kb.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "kb_document")
public class KbDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_msg", columnDefinition = "text")
    private String errorMsg;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(nullable = false)
    private Boolean deleted = false;
}
