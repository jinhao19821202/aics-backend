package com.aics.m003_kb.service;

import com.aics.m003_kb.domain.KbFaqGroup;
import lombok.Data;

import java.time.OffsetDateTime;

public class KbFaqGroupDto {

    @Data
    public static class Request {
        private String name;
        private String description;
        private Integer sortOrder;
    }

    @Data
    public static class Response {
        private Long id;
        private Long tenantId;
        private String name;
        private String description;
        private Integer sortOrder;
        private long faqCount;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static Response of(KbFaqGroup g, long faqCount) {
            Response r = new Response();
            r.id = g.getId();
            r.tenantId = g.getTenantId();
            r.name = g.getName();
            r.description = g.getDescription();
            r.sortOrder = g.getSortOrder();
            r.faqCount = faqCount;
            r.createdAt = g.getCreatedAt();
            r.updatedAt = g.getUpdatedAt();
            return r;
        }
    }
}
