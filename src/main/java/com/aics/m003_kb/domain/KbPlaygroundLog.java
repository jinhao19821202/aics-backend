package com.aics.m003_kb.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "kb_playground_log")
public class KbPlaygroundLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, columnDefinition = "text")
    private String query;

    @Column(nullable = false)
    private String channel = "both";

    @Column(name = "top_k", nullable = false)
    private Integer topK = 10;

    @Column(nullable = false)
    private BigDecimal threshold = new BigDecimal("0.500");

    @Column(name = "hit_count", nullable = false)
    private Integer hitCount = 0;

    @Column(name = "top_score")
    private BigDecimal topScore;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> filters = Map.of();

    @Column(name = "top_doc_id")
    private Long topDocId;

    @Column(name = "top_chunk_id")
    private Long topChunkId;

    @Column(name = "rerank_used", nullable = false)
    private Boolean rerankUsed = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
