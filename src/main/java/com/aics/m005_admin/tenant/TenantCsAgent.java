package com.aics.m005_admin.tenant;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "tenant_cs_agent")
public class TenantCsAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    private String description;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "persona_prompt")
    private String personaPrompt;

    private String greeting;

    /** 软引用 tenant_llm_config.id；null 则使用租户默认 chat 配置。 */
    @Column(name = "chat_llm_config_id")
    private Long chatLlmConfigId;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
