package com.aics.m005_admin.controller;

import com.aics.common.ApiResponse;
import com.aics.common.BizException;
import com.aics.common.PageResult;
import com.aics.common.tenant.TenantContext;
import com.aics.m003_kb.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * F004 调试页支持：chunk 详情（带上下文）+ 文档下 chunk 分页 + 文档最近召回历史。
 */
@RestController
@RequestMapping("/api/admin/kb")
@RequiredArgsConstructor
public class KbChunkController {

    private final KbChunkRepository chunkRepo;
    private final KbDocumentRepository docRepo;
    private final KbPlaygroundLogRepository playgroundLogRepo;

    @GetMapping("/chunks/{id}")
    @PreAuthorize("hasAuthority('tenant:kb:read') or hasAuthority('tenant:kb:manage')")
    public ApiResponse<ChunkDetail> get(@PathVariable Long id) {
        Long tenantId = TenantContext.require();
        KbChunk c = chunkRepo.findById(id)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> new BizException(404, "chunk not found"));
        KbDocument doc = docRepo.findByIdAndTenantId(c.getDocId(), tenantId).orElse(null);

        ChunkDetail out = new ChunkDetail();
        out.id = c.getId();
        out.docId = c.getDocId();
        out.docTitle = doc == null ? null : doc.getTitle();
        out.docTags = doc == null ? List.of() : (doc.getTags() == null ? List.of() : doc.getTags());
        out.content = c.getContent();
        out.enabled = c.getEnabled();
        out.meta = c.getMeta();
        out.createdAt = c.getCreatedAt();

        chunkRepo.findFirst1ByTenantIdAndDocIdAndIdLessThanOrderByIdDesc(tenantId, c.getDocId(), c.getId())
                .stream().findFirst().ifPresent(prev -> {
                    out.prevId = prev.getId();
                    out.prevPreview = preview(prev.getContent());
                });
        chunkRepo.findFirst1ByTenantIdAndDocIdAndIdGreaterThanOrderByIdAsc(tenantId, c.getDocId(), c.getId())
                .stream().findFirst().ifPresent(next -> {
                    out.nextId = next.getId();
                    out.nextPreview = preview(next.getContent());
                });
        return ApiResponse.ok(out);
    }

    @GetMapping("/documents/{id}/chunks")
    @PreAuthorize("hasAuthority('tenant:kb:read') or hasAuthority('tenant:kb:manage')")
    public ApiResponse<PageResult<ChunkListItem>> listByDoc(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        Long tenantId = TenantContext.require();
        docRepo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new BizException(404, "document not found"));
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), safeSize);
        Page<KbChunk> pg = chunkRepo.findByTenantIdAndDocIdOrderByIdAsc(tenantId, id, pr);
        Page<ChunkListItem> mapped = pg.map(c -> {
            ChunkListItem li = new ChunkListItem();
            li.id = c.getId();
            li.content = preview(c.getContent());
            li.enabled = c.getEnabled();
            li.length = c.getContent() == null ? 0 : c.getContent().length();
            return li;
        });
        return ApiResponse.ok(PageResult.of(mapped));
    }

    @GetMapping("/documents/{id}/playground-logs")
    @PreAuthorize("hasAuthority('tenant:kb:read') or hasAuthority('tenant:kb:manage')")
    public ApiResponse<List<PlaygroundLogItem>> recentLogs(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "10") int limit) {
        Long tenantId = TenantContext.require();
        docRepo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new BizException(404, "document not found"));
        int safe = Math.min(Math.max(limit, 1), 50);
        List<KbPlaygroundLog> logs = playgroundLogRepo
                .findByTenantIdAndTopDocIdOrderByCreatedAtDesc(tenantId, id, PageRequest.of(0, safe));
        List<PlaygroundLogItem> out = logs.stream().map(l -> {
            PlaygroundLogItem i = new PlaygroundLogItem();
            i.id = l.getId();
            i.query = l.getQuery();
            i.topK = l.getTopK();
            i.threshold = l.getThreshold() == null ? null : l.getThreshold().doubleValue();
            i.hitCount = l.getHitCount();
            i.topScore = l.getTopScore() == null ? null : l.getTopScore().doubleValue();
            i.topChunkId = l.getTopChunkId();
            i.latencyMs = l.getLatencyMs();
            i.rerankUsed = Boolean.TRUE.equals(l.getRerankUsed());
            i.filters = l.getFilters();
            i.createdAt = l.getCreatedAt();
            return i;
        }).toList();
        return ApiResponse.ok(out);
    }

    private static String preview(String s) {
        if (s == null) return null;
        return s.length() <= 200 ? s : s.substring(0, 200);
    }

    public static class ChunkDetail {
        public Long id;
        public Long docId;
        public String docTitle;
        public List<String> docTags;
        public String content;
        public Boolean enabled;
        public Map<String, Object> meta;
        public OffsetDateTime createdAt;
        public Long prevId;
        public String prevPreview;
        public Long nextId;
        public String nextPreview;
    }

    public static class ChunkListItem {
        public Long id;
        public String content;
        public Boolean enabled;
        public int length;
    }

    public static class PlaygroundLogItem {
        public Long id;
        public String query;
        public Integer topK;
        public Double threshold;
        public Integer hitCount;
        public Double topScore;
        public Long topChunkId;
        public Integer latencyMs;
        public Boolean rerankUsed;
        public Map<String, Object> filters;
        public OffsetDateTime createdAt;
    }
}
