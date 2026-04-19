package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.common.BizException;
import com.aics.common.tenant.TenantContext;
import com.aics.m003_kb.domain.KbDocument;
import com.aics.m003_kb.domain.KbDocumentRepository;
import com.aics.m003_kb.domain.KbPlaygroundLog;
import com.aics.m003_kb.domain.KbPlaygroundLogRepository;
import com.aics.m003_kb.service.KnowledgeSearchService;
import com.aics.m005_admin.user.AdminPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * F004 召回调试：输入 query + 可选 topK / threshold / docIds / tags / disableRerank，返回详细候选与分项得分。
 * 每用户每分钟 20 次（Redis 计数），防止滥用 embedding 配额。
 * 每次调用都会落 kb_playground_log 供"某文档最近被召回"的反查。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/kb/playground")
@RequiredArgsConstructor
public class KbPlaygroundController {

    private static final int RL_LIMIT_PER_MIN = 20;

    private final KnowledgeSearchService searchService;
    private final KbDocumentRepository docRepo;
    private final KbPlaygroundLogRepository logRepo;
    private final StringRedisTemplate redis;

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:kb:read') or hasAuthority('tenant:kb:manage')")
    public ApiResponse<KnowledgeSearchService.DebugResult> search(@RequestBody PlaygroundRequest req,
                                                                  @AuthenticationPrincipal AdminPrincipal op) {
        if (req == null || req.getQuery() == null || req.getQuery().isBlank()) {
            throw new BizException(400, "query required");
        }
        if (req.getQuery().length() > 500) {
            throw new BizException(400, "query too long");
        }

        Long tenantId = TenantContext.require();
        enforceRateLimit(op, tenantId);

        int topK = req.getTopK() == null ? KnowledgeSearchService.DEFAULT_TOP_K
                : Math.min(Math.max(req.getTopK(), 1), 50);
        double threshold = req.getThreshold() == null ? KnowledgeSearchService.DEFAULT_THRESHOLD
                : Math.min(Math.max(req.getThreshold(), 0.0), 1.0);
        List<Long> docIds = normalizeDocIds(tenantId, req.getDocIds());
        List<String> tags = normalizeTags(req.getTags());
        boolean disableRerank = Boolean.TRUE.equals(req.getDisableRerank());

        KnowledgeSearchService.DebugResult result = searchService.searchForDebug(
                req.getQuery(), topK, threshold, docIds, tags, disableRerank);

        persistLog(tenantId, op, req.getQuery(), topK, threshold, docIds, tags, disableRerank, result);
        return ApiResponse.ok(result);
    }

    private List<Long> normalizeDocIds(Long tenantId, List<Long> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<Long> distinct = raw.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return List.of();
        if (distinct.size() > 50) {
            throw new BizException(400, "docIds 上限 50");
        }
        List<KbDocument> found = docRepo.findByTenantIdAndIdInAndDeletedFalse(tenantId, distinct);
        Set<Long> validIds = found.stream().map(KbDocument::getId).collect(Collectors.toSet());
        List<Long> missing = distinct.stream().filter(id -> !validIds.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new BizException(400, "docIds 非法或已删除: " + missing);
        }
        return distinct;
    }

    private List<String> normalizeTags(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<String> distinct = raw.stream()
                .filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().toList();
        if (distinct.size() > 20) {
            throw new BizException(400, "tags 上限 20");
        }
        return distinct;
    }

    private void persistLog(Long tenantId, AdminPrincipal op, String query, int topK, double threshold,
                            List<Long> docIds, List<String> tags, boolean disableRerank,
                            KnowledgeSearchService.DebugResult result) {
        try {
            KbPlaygroundLog log = new KbPlaygroundLog();
            log.setTenantId(tenantId);
            log.setUserId(op == null ? null : op.id());
            log.setQuery(query);
            log.setChannel("admin");
            log.setTopK(topK);
            log.setThreshold(BigDecimal.valueOf(threshold).setScale(3, RoundingMode.HALF_UP));
            int hitCount = result.candidates == null ? 0 : result.candidates.size();
            log.setHitCount(hitCount);
            if (hitCount > 0) {
                log.setTopScore(BigDecimal.valueOf(result.candidates.get(0).finalScore)
                        .setScale(4, RoundingMode.HALF_UP));
                log.setTopDocId(result.candidates.get(0).docId);
                log.setTopChunkId(result.candidates.get(0).chunkId);
            }
            log.setLatencyMs((int) Math.min(Integer.MAX_VALUE, result.elapsedMs));
            log.setRerankUsed(result.rerankUsed);
            Map<String, Object> filters = new LinkedHashMap<>();
            if (!docIds.isEmpty()) filters.put("docIds", docIds);
            if (!tags.isEmpty()) filters.put("tags", tags);
            if (disableRerank) filters.put("disableRerank", true);
            if (result.rerankStatus != null) filters.put("rerankStatus", result.rerankStatus);
            log.setFilters(filters);
            logRepo.save(log);
        } catch (Exception e) {
            // 日志写入失败不影响主链路
            KbPlaygroundController.log.warn("kb playground log persist failed: {}", e.getMessage());
        }
    }

    private void enforceRateLimit(AdminPrincipal op, Long tenantId) {
        Long uid = op == null ? 0L : op.id();
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "kb:pg:rl:t" + tenantId + ":u" + uid + ":m" + minute;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(70));
        }
        if (count != null && count > RL_LIMIT_PER_MIN) {
            throw new BizException(429, "调试频率过高，请稍后再试");
        }
    }

    @Data
    public static class PlaygroundRequest {
        private String query;
        private Integer topK;
        private Double threshold;
        private List<Long> docIds;
        private List<String> tags;
        private Boolean disableRerank;
    }
}
