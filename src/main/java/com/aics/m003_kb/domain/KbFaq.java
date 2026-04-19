package com.aics.m003_kb.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "kb_faq")
public class KbFaq {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** P004-B F003：所属分组；V17 迁移后非空约束由 CHECK 保证（避免全表锁）。 */
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column
    private String keywords;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Type(JsonType.class)
    @Column(name = "question_vector", columnDefinition = "jsonb")
    private List<Double> questionVector;

    @Column(name = "question_vector_dim")
    private Integer questionVectorDim;

    @Column(name = "question_vector_updated_at")
    private OffsetDateTime questionVectorUpdatedAt;
}
