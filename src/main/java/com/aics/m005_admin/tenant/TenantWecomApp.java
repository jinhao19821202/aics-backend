package com.aics.m005_admin.tenant;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "tenant_wecom_app")
public class TenantWecomApp {

    public static final String STATUS_NOT_VERIFIED = "NOT_VERIFIED";
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "corp_id", nullable = false)
    private String corpId;

    @Column(name = "agent_id", nullable = false)
    private Integer agentId;

    @Column(nullable = false)
    private String token;

    @Column(name = "aes_key_cipher", nullable = false)
    private String aesKeyCipher;

    @Column(name = "secret_cipher", nullable = false)
    private String secretCipher;

    @Column(name = "bot_userid")
    private String botUserid;

    @Column(name = "api_base")
    private String apiBase;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private String status = STATUS_NOT_VERIFIED;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "last_test_at")
    private OffsetDateTime lastTestAt;

    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    @Column(name = "last_test_msg")
    private String lastTestMsg;

    @Column(name = "cs_agent_id")
    private Long csAgentId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
