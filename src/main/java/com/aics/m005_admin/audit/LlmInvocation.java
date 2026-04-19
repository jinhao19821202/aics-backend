package com.aics.m005_admin.audit;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
@Entity
@Table(name = "llm_invocation")
public class LlmInvocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "trigger_msg_id")
    private String triggerMsgId;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String prompt;

    private String response;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Type(JsonType.class)
    @Column(name = "references_used", columnDefinition = "jsonb")
    private List<Map<String, Object>> referencesUsed;

    private BigDecimal confidence;

    @Column(nullable = false)
    private Boolean handoff = false;

    @Column(nullable = false)
    private String status = "OK";

    @Column(name = "error_msg")
    private String errorMsg;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
