package com.aics.m005_admin.csagent;

import com.aics.m005_admin.tenant.TenantCsAgent;
import lombok.Data;

import java.time.OffsetDateTime;

public class TenantCsAgentDto {

    @Data
    public static class Request {
        private String name;
        private String code;
        private String description;
        private String avatarUrl;
        private String personaPrompt;
        private String greeting;
        /** 软引用 tenant_llm_config.id；null/缺省 = 使用租户默认 chat 配置。 */
        private Long chatLlmConfigId;
        private Boolean enabled;
    }

    @Data
    public static class Response {
        private Long id;
        private Long tenantId;
        private String name;
        private String code;
        private String description;
        private String avatarUrl;
        private String personaPrompt;
        private String greeting;
        private Long chatLlmConfigId;
        private String chatLlmConfigLabel;
        private Boolean enabled;
        private Long boundWecomAppId;
        private String boundWecomAppName;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static Response of(TenantCsAgent a) {
            Response r = new Response();
            r.id = a.getId();
            r.tenantId = a.getTenantId();
            r.name = a.getName();
            r.code = a.getCode();
            r.description = a.getDescription();
            r.avatarUrl = a.getAvatarUrl();
            r.personaPrompt = a.getPersonaPrompt();
            r.greeting = a.getGreeting();
            r.chatLlmConfigId = a.getChatLlmConfigId();
            r.enabled = a.getEnabled();
            r.createdAt = a.getCreatedAt();
            r.updatedAt = a.getUpdatedAt();
            return r;
        }
    }

    @Data
    public static class BindRequest {
        private Long wecomAppId;
    }
}
