package com.aics.m005_admin.csagent;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "cs_agent_faq_group_mapping")
public class CsAgentFaqGroupMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "cs_agent_id", nullable = false)
    private Long csAgentId;

    @Column(name = "faq_group_id", nullable = false)
    private Long faqGroupId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
