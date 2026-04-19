package com.aics.m001_message.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "wecom_message")
public class WeComMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "msg_id", nullable = false)
    private String msgId;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "from_userid")
    private String fromUserid;

    @Column(name = "from_name")
    private String fromName;

    @Column(name = "msg_type", nullable = false)
    private String msgType;

    @Column(columnDefinition = "text")
    private String content;

    @Type(JsonType.class)
    @Column(name = "mentioned_list", columnDefinition = "jsonb")
    private List<String> mentionedList;

    @Column(columnDefinition = "text")
    private String raw;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
