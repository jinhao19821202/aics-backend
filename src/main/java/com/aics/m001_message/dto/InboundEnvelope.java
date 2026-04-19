package com.aics.m001_message.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Kafka 投递消息体（M001 -> Worker）。 */
@Data
@NoArgsConstructor
public class InboundEnvelope {
    private Long tenantId;
    private String msgId;
    private String groupId;
    private String fromUserid;
    private String fromName;
    private String msgType;
    private String content;
    private List<String> mentionedList = new ArrayList<>();
    private long createTime;
    private String conversationId;
    /**
     * P004-A F002：该消息所属的智能客服 Agent id（由 wecomApp.csAgentId 决定）。
     * 过渡期允许为 null：老生产者未填 → Worker 回退到租户默认流程（等价于无 Agent 覆盖）。
     */
    private Long csAgentId;
}
