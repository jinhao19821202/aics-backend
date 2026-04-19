package com.aics.m003_kb.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "kb_chunk")
public class KbChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "doc_id", nullable = false)
    private Long docId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "milvus_id")
    private Long milvusId;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> meta;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
