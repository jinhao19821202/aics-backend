package com.aics.m001_message.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "session_message")
public class SessionMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "msg_id", nullable = false)
    private String msgId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name")
    private String userName;

    @Column(nullable = false)
    private String role;   // user / bot / agent

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
