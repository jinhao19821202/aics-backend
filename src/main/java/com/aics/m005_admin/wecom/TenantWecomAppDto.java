package com.aics.m005_admin.wecom;

import com.aics.m005_admin.tenant.TenantWecomApp;
import lombok.Data;

import java.time.OffsetDateTime;

public class TenantWecomAppDto {

    @Data
    public static class Request {
        private String name;
        private String corpId;
        private Integer agentId;
        private String token;
        /** 明文；为空表示不修改（update 场景）；create 必填。 */
        private String aesKey;
        /** 明文；为空表示不修改（update 场景）；create 必填。 */
        private String secret;
        private String botUserid;
        private String apiBase;
        private Boolean enabled;
    }

    @Data
    public static class Response {
        private Long id;
        private String name;
        private String corpId;
        private Integer agentId;
        private String tokenTail;
        private String aesKeyTail;
        private String secretTail;
        private String botUserid;
        private String apiBase;
        private Boolean enabled;
        private String status;
        private OffsetDateTime verifiedAt;
        private OffsetDateTime lastTestAt;
        private Boolean lastTestOk;
        private String lastTestMsg;
        private Long csAgentId;
        private String csAgentName;
        private String callbackUrl;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static Response of(TenantWecomApp a, String callbackUrl, String csAgentName) {
            Response r = new Response();
            r.id = a.getId();
            r.name = a.getName();
            r.corpId = a.getCorpId();
            r.agentId = a.getAgentId();
            r.tokenTail = tail(a.getToken(), 4);
            r.aesKeyTail = "●●●●";
            r.secretTail = "●●●●";
            r.botUserid = a.getBotUserid();
            r.apiBase = a.getApiBase();
            r.enabled = a.getEnabled();
            r.status = a.getStatus();
            r.verifiedAt = a.getVerifiedAt();
            r.lastTestAt = a.getLastTestAt();
            r.lastTestOk = a.getLastTestOk();
            r.lastTestMsg = a.getLastTestMsg();
            r.csAgentId = a.getCsAgentId();
            r.csAgentName = csAgentName;
            r.callbackUrl = callbackUrl;
            r.createdAt = a.getCreatedAt();
            r.updatedAt = a.getUpdatedAt();
            return r;
        }

        private static String tail(String s, int n) {
            if (s == null || s.length() <= n) return s;
            return "…" + s.substring(s.length() - n);
        }
    }

    @Data
    public static class TestResult {
        private boolean ok;
        private String message;
        private Long latencyMs;
    }

    @Data
    public static class CallbackUrlResponse {
        private String callbackUrl;
    }
}
