package com.aics.m005_admin.audit;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_name")
    private String adminName;

    @Column(nullable = false)
    private String action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Type(JsonType.class)
    @Column(name = "before_val", columnDefinition = "jsonb")
    private Map<String, Object> beforeVal;

    @Type(JsonType.class)
    @Column(name = "after_val", columnDefinition = "jsonb")
    private Map<String, Object> afterVal;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
