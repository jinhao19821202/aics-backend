package com.aics.m002_dialog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult {
    private boolean hasResult;
    private double topConfidence;
    private List<Reference> references = new ArrayList<>();
    private FaqHit faqHit;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reference {
        private Long chunkId;
        private Long docId;
        private double score;
        private String content;
        private String source;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaqHit {
        private Long faqId;
        private String question;
        private String answer;
        private double confidence;
        /** exact / semantic / keyword；便于调试页展示命中方式。*/
        private String matchedBy;

        public FaqHit(Long faqId, String question, String answer, double confidence) {
            this(faqId, question, answer, confidence, null);
        }
    }
}
