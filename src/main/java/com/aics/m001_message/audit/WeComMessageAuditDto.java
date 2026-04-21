package com.aics.m001_message.audit;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * P005 F003：企微消息审计 DTO。
 */
public class WeComMessageAuditDto {

    @Data
    public static class ListItem {
        private Long id;
        private OffsetDateTime createdAt;
        private Long wecomAppId;
        private String wecomAppName;
        private String chatId;
        private String fromUserid;
        private String msgType;
        private String contentPreview;
        private String verifyStatus;
        private Boolean mentionedBot;
    }

    @Data
    public static class Detail {
        private Long id;
        private Long tenantId;
        private Long wecomAppId;
        private WeComAppBrief wecomApp;
        private CsAgentBrief csAgent;
        private String msgId;
        private String chatId;
        private String fromUserid;
        private String fromName;
        private String msgType;
        private String content;
        private List<String> mentionedList;
        private String verifyStatus;
        private String raw;
        private String encryptedPayload;
        private String msgSignature;
        private String timestamp;
        private String nonce;
        private OffsetDateTime createdAt;
        /** 关联 session_message 行 id（role=user，同 msg_id）。前端可跳转会话审计。 */
        private Long linkedSessionMsgId;
    }

    @Data
    public static class WeComAppBrief {
        private Long id;
        private String name;
        private String corpId;
        private Integer agentId;
    }

    @Data
    public static class CsAgentBrief {
        private Long id;
        private String name;
        private String code;
    }
}
