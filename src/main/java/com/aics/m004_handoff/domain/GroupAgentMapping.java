package com.aics.m004_handoff.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "group_agent_mapping")
public class GroupAgentMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "group_name")
    private String groupName;

    @Type(JsonType.class)
    @Column(name = "agent_userids", columnDefinition = "jsonb")
    private List<String> agentUserids;

    @Column(name = "default_agent")
    private String defaultAgent;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
