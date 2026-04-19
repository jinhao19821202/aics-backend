package com.aics.m005_admin.csagent;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

public class CsAgentContentMappingDto {

    @Data
    public static class ReplaceRequest {
        private List<Long> ids;
    }

    @Data
    public static class FaqGroupItem {
        private Long faqGroupId;
        private String name;
        private Long faqCount;
        private OffsetDateTime createdAt;
    }

    @Data
    public static class DocumentItem {
        private Long kbDocumentId;
        private String title;
        private String status;
        private OffsetDateTime createdAt;
    }
}
