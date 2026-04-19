package com.aics.m001_message.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContextSnapshot {
    private String groupId;
    private TriggerMsg triggerMsg;
    private List<HistoryItem> history;
    private String conversationId;
    /** P004-A F002：触发时所属的 Agent；下游 PromptBuilder / LlmClientResolver 可据此做覆盖。null = 无 Agent 覆盖。 */
    private Long csAgentId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerMsg {
        private String msgId;
        private String user;
        private String userName;
        private String text;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem {
        private String user;
        private String text;
        private boolean isBot;
        private long ts;
    }
}
