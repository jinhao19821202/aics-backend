package com.aics.m005_admin.llm;

import com.aics.m005_admin.tenant.TenantLlmConfig;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class LlmConfigDto {

    @Data
    public static class Request {
        private String provider = "dashscope";
        private String purpose;
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer embeddingDim;
        private Map<String, Object> params = new HashMap<>();
        private Boolean isDefault = false;
        private Boolean enabled = true;
    }

    @Data
    public static class Response {
        private Long id;
        private Long tenantId;
        private String provider;
        private String purpose;
        private String apiKeyTail;
        private String baseUrl;
        private String model;
        private Integer embeddingDim;
        private Map<String, Object> params;
        private Boolean isDefault;
        private Boolean enabled;
        private OffsetDateTime lastTestAt;
        private Boolean lastTestOk;
        private String lastTestMsg;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static Response of(TenantLlmConfig c) {
            Response r = new Response();
            r.id = c.getId();
            r.tenantId = c.getTenantId();
            r.provider = c.getProvider();
            r.purpose = c.getPurpose();
            r.apiKeyTail = c.getApiKeyTail();
            r.baseUrl = c.getBaseUrl();
            r.model = c.getModel();
            r.embeddingDim = c.getEmbeddingDim();
            r.params = c.getParams();
            r.isDefault = c.getIsDefault();
            r.enabled = c.getEnabled();
            r.lastTestAt = c.getLastTestAt();
            r.lastTestOk = c.getLastTestOk();
            r.lastTestMsg = c.getLastTestMsg();
            r.createdAt = c.getCreatedAt();
            r.updatedAt = c.getUpdatedAt();
            return r;
        }
    }

    @Data
    public static class TestResult {
        private boolean ok;
        private String message;
        private Long latencyMs;
    }
}
