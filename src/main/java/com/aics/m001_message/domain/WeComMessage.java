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

    /** P005 F002：原始 &lt;Encrypt&gt; 密文（Base64）。验签失败/历史数据为 NULL。 */
    @Column(name = "encrypted_payload", columnDefinition = "text")
    private String encryptedPayload;

    @Column(name = "msg_signature", length = 64)
    private String msgSignature;

    /** 企微 GET 参数 timestamp，保持字符串原样，避免时区歧义。 */
    @Column(name = "timestamp_str", length = 20)
    private String timestampStr;

    @Column(length = 64)
    private String nonce;

    /** 路由到的 tenant_wecom_app.id；默认租户兼容路径可为 NULL。 */
    @Column(name = "wecom_app_id")
    private Long wecomAppId;

    /** VERIFIED / REJECTED / UNKNOWN（历史数据）。 */
    @Column(name = "verify_status", length = 16, nullable = false)
    private String verifyStatus = "UNKNOWN";

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static final String VERIFY_VERIFIED = "VERIFIED";
    public static final String VERIFY_REJECTED = "REJECTED";
    public static final String VERIFY_UNKNOWN  = "UNKNOWN";
}
