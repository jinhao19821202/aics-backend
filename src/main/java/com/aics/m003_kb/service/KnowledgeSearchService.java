package com.aics.m003_kb.service;

import com.aics.common.tenant.TenantContext;
import com.aics.infra.milvus.MilvusVectorStore;
import com.aics.m002_dialog.dto.RetrievalResult;
import com.aics.m003_kb.domain.KbChunk;
import com.aics.m003_kb.domain.KbChunkRepository;
import com.aics.m003_kb.domain.KbDocument;
import com.aics.m003_kb.domain.KbDocumentRepository;
import com.aics.m005_admin.csagent.CsAgentContentMappingService;
import com.aics.m005_admin.llm.LlmClientResolver;
import com.aics.m005_admin.tenant.CollectionProvisioner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * M003 F002：FAQ + 向量检索 + 精排（rerank）。
 *
 * 生产链路：FAQ 命中 → 否则 粗排 topK*3（min 30） → sourceWeight 融合 → 阈值过滤 → （可选）精排 → Top-K。
 * - 若租户配置了 purpose=rerank 的默认 LlmConfig 且未显式 disableRerank：调用 rerank，以 rerankScore 作为 finalScore。
 * - rerank 失败（超时 / 5xx）：warn 日志 + 指标，回退到原粗排结果排序（P003 F005 决策）。
 *
 * 调试链路：searchForDebug 返回 FAQ 命中 + 所有候选（含 disabled）+ 分项分；按 docIds/tags 可进一步缩窄 Milvus 表达式。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeSearchService {

    public static final double DEFAULT_THRESHOLD = 0.5;
    public static final int DEFAULT_TOP_K = 10;
    public static final int DEFAULT_REF_LIMIT = 3;

    private final FaqService faqService;
    private final LlmClientResolver llmResolver;
    private final MilvusVectorStore milvus;
    private final KbChunkRepository chunkRepo;
    private final KbDocumentRepository docRepo;
    private final CollectionProvisioner collectionProvisioner;
    private final CsAgentContentMappingService agentContentMapping;

    public RetrievalResult search(String query) {
        return search(query, DEFAULT_TOP_K, DEFAULT_THRESHOLD);
    }

    public RetrievalResult search(String query, int topK, double threshold) {
        return search(SearchRequest.builder(query).topK(topK).threshold(threshold).build()).toRetrievalResult();
    }

    /** P004-B F004：带 Agent 过滤的生产检索入口。csAgentId=null 等价于老行为。 */
    public RetrievalResult search(String query, Long csAgentId) {
        return search(SearchRequest.builder(query).csAgentId(csAgentId).build()).toRetrievalResult();
    }

    /** 生产检索入口：FAQ → 向量（含精排） → 组装 RetrievalResult。*/
    public SearchOutcome search(SearchRequest req) {
        SearchOutcome out = new SearchOutcome();
        out.topK = req.topK;
        out.threshold = req.threshold;

        Set<Long> allowedGroupIds = agentContentMapping.allowedFaqGroupIds(req.csAgentId);
        RetrievalResult.FaqHit faq = faqService.match(req.query, allowedGroupIds);
        out.faqHit = faq;
        if (faq != null) {
            out.topConfidence = faq.getConfidence();
            out.hasResult = true;
            return out;
        }

        try {
            Long tenantId = TenantContext.require();
            List<Scored> scored = vectorSearch(tenantId, req, false, out);
            if (scored.isEmpty()) {
                out.hasResult = false;
                return out;
            }
            scored.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
            out.candidates = scored;
            out.topConfidence = scored.get(0).finalScore;
            out.hasResult = true;
            return out;
        } catch (Exception e) {
            log.error("knowledge search failed: {}", e.getMessage());
            out.errorMsg = e.getMessage();
            out.hasResult = false;
            return out;
        }
    }

    /** 调试用：返回 FAQ 命中 + 所有向量候选（含 disabled）+ 分项得分 + 精排信息。*/
    public DebugResult searchForDebug(String query, int topK, double threshold,
                                      List<Long> docIds, List<String> tags, boolean disableRerank) {
        long started = System.currentTimeMillis();
        DebugResult d = new DebugResult();
        d.topK = topK;
        d.threshold = threshold;
        d.disableRerank = disableRerank;

        RetrievalResult.FaqHit faq = faqService.match(query);
        d.faqHit = faq;

        SearchOutcome out = new SearchOutcome();
        Long tenantId = TenantContext.require();
        try {
            SearchRequest req = SearchRequest.builder(query)
                    .topK(topK)
                    .threshold(threshold)
                    .docIds(docIds)
                    .tags(tags)
                    .disableRerank(disableRerank)
                    .includeDisabled(true)
                    .build();
            List<Scored> scored = vectorSearch(tenantId, req, true, out);
            scored.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
            List<DebugCandidate> candidates = new ArrayList<>();
            for (Scored s : scored) {
                DebugCandidate c = new DebugCandidate();
                c.chunkId = s.chunk.getId();
                c.docId = s.doc == null ? null : s.doc.getId();
                c.docTitle = s.doc == null ? null : s.doc.getTitle();
                c.docTags = s.doc == null ? List.of() : (s.doc.getTags() == null ? List.of() : s.doc.getTags());
                c.content = truncate(s.chunk.getContent(), 500);
                c.enabled = Boolean.TRUE.equals(s.chunk.getEnabled());
                c.rawScore = s.rawScore;
                c.sourceWeight = s.sourceWeight;
                c.rerankScore = s.rerankScore;
                c.finalScore = s.finalScore;
                candidates.add(c);
            }
            d.candidates = candidates;
            d.rerankUsed = out.rerankUsed;
            d.rerankStatus = out.rerankStatus;
            d.rerankProvider = out.rerankProvider;
            d.rerankModel = out.rerankModel;
        } catch (Exception e) {
            log.error("playground search failed", e);
            String msg = e.getMessage();
            d.errorMsg = (msg == null || msg.isBlank())
                    ? e.getClass().getSimpleName()
                    : msg;
            d.candidates = List.of();
        }
        d.elapsedMs = System.currentTimeMillis() - started;
        return d;
    }

    private List<Scored> vectorSearch(Long tenantId, SearchRequest req,
                                      boolean keepForDebug, SearchOutcome outCtx) {
        Collection<Long> userDocIds = req.effectiveDocIds(docRepo, tenantId);
        Set<Long> agentAllowedDocs = agentContentMapping.allowedDocumentIds(req.csAgentId);
        Collection<Long> finalDocIds = intersectDocIds(userDocIds, agentAllowedDocs);
        // agent 配置了文档白名单 && 与 user 指定取交集后为空 → 直接空结果
        if (agentAllowedDocs != null && (finalDocIds == null || finalDocIds.isEmpty())) {
            return new ArrayList<>();
        }

        float[] v = llmResolver.embedOne(tenantId, req.query);
        String collection = collectionProvisioner.collectionFor(tenantId);

        // 粗排：取 topK * 3（最低 30）候选，留给精排/过滤余量
        int coarseK = Math.max(30, req.topK * 3);
        String expr = buildDocIdExpr(finalDocIds);
        List<MilvusVectorStore.SearchHit> hits = milvus.search(collection, v, coarseK, (float) req.threshold, expr);
        if (hits.isEmpty()) return new ArrayList<>();

        List<Long> chunkIds = hits.stream().map(h -> h.chunkId).toList();
        Map<Long, KbChunk> chunkMap = chunkRepo.findAllById(chunkIds).stream()
                .filter(x -> tenantId.equals(x.getTenantId()))
                .collect(Collectors.toMap(KbChunk::getId, x -> x));
        Set<Long> docIdSet = hits.stream().map(h -> h.docId).collect(Collectors.toSet());
        Map<Long, KbDocument> docMap = docRepo.findAllById(docIdSet).stream()
                .filter(x -> tenantId.equals(x.getTenantId()))
                .collect(Collectors.toMap(KbDocument::getId, x -> x));

        List<Scored> scored = new ArrayList<>();
        for (var h : hits) {
            KbChunk c = chunkMap.get(h.chunkId);
            if (c == null) continue;
            if (!req.includeDisabled && !Boolean.TRUE.equals(c.getEnabled())) continue;
            KbDocument d = docMap.get(c.getDocId());
            double sourceWeight = weightOf(d);
            double coarseScore = 0.7 * h.score + 0.3 * sourceWeight;
            Scored s = new Scored();
            s.hit = h;
            s.chunk = c;
            s.doc = d;
            s.rawScore = h.score;
            s.sourceWeight = sourceWeight;
            s.finalScore = coarseScore;
            scored.add(s);
        }
        if (scored.isEmpty()) return scored;

        // 精排（F005）：按 finalScore 降序后取 Top-N 送 rerank；失败回退。
        if (!req.disableRerank) {
            applyRerank(tenantId, req, scored, outCtx);
        } else {
            outCtx.rerankUsed = false;
            outCtx.rerankStatus = "disabled";
        }

        // 取 Top-K（生产路径）；debug 保留全部供前端观察
        if (!keepForDebug && scored.size() > req.topK) {
            scored.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
            return new ArrayList<>(scored.subList(0, req.topK));
        }
        return scored;
    }

    private void applyRerank(Long tenantId, SearchRequest req, List<Scored> scored, SearchOutcome outCtx) {
        scored.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));
        int rerankIn = Math.min(scored.size(), Math.max(req.topK * 3, 30));
        List<Scored> slice = scored.subList(0, rerankIn);
        List<String> docs = slice.stream().map(s -> s.chunk.getContent()).toList();

        Optional<LlmClientResolver.RerankOutcome> maybe;
        try {
            maybe = llmResolver.rerank(tenantId, req.query, docs, req.topK);
        } catch (Exception e) {
            log.warn("rerank invoke failed tenant={}: {} — fallback to raw order", tenantId, e.getMessage());
            outCtx.rerankUsed = false;
            outCtx.rerankStatus = "failed";
            return;
        }
        if (maybe.isEmpty()) {
            outCtx.rerankUsed = false;
            outCtx.rerankStatus = "not_configured";
            return;
        }
        LlmClientResolver.RerankOutcome r = maybe.get();
        if (r.scores == null || r.scores.size() != slice.size()) {
            log.warn("rerank returned {} scores for {} docs tenant={} — fallback",
                    r.scores == null ? "null" : r.scores.size(), slice.size(), tenantId);
            outCtx.rerankUsed = false;
            outCtx.rerankStatus = "failed";
            return;
        }
        for (int i = 0; i < slice.size(); i++) {
            Double rs = r.scores.get(i);
            if (rs == null) continue;
            slice.get(i).rerankScore = rs;
            slice.get(i).finalScore = rs;
        }
        if (r.scoreFloor != null) {
            scored.removeIf(s -> s.rerankScore != null && s.rerankScore < r.scoreFloor);
        }
        outCtx.rerankUsed = true;
        outCtx.rerankStatus = "ok";
        outCtx.rerankProvider = r.provider;
        outCtx.rerankModel = r.model;
    }

    private String buildDocIdExpr(Collection<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) return null;
        return docIds.stream().map(String::valueOf)
                .collect(Collectors.joining(",", MilvusVectorStore.F_DOC + " in [", "]"));
    }

    /**
     * 与 Agent 允许的文档白名单取交集：
     * - agent 无映射（null）→ 用户指定什么就是什么（可能也是空）
     * - agent 有映射，用户未指定 → 直接用 agent 白名单
     * - 两者都指定 → 交集（用户 ∩ agent）；可能为空（调用方需要判空并短路）
     */
    private static Collection<Long> intersectDocIds(Collection<Long> userDocIds, Set<Long> agentAllowed) {
        if (agentAllowed == null) return userDocIds;
        if (userDocIds == null || userDocIds.isEmpty()) return agentAllowed;
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        for (Long id : userDocIds) {
            if (agentAllowed.contains(id)) out.add(id);
        }
        return out;
    }

    private double weightOf(KbDocument d) {
        if (d == null) return 0.7;
        if (d.getTags() != null && d.getTags().contains("官方手册")) return 0.9;
        return 0.7;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ------------------------------------------------------------
    // DTO
    // ------------------------------------------------------------

    public static class SearchRequest {
        public String query;
        public int topK = DEFAULT_TOP_K;
        public double threshold = DEFAULT_THRESHOLD;
        public List<Long> docIds;           // 明确传入 → 仅本租户校验后的 id
        public List<String> tags;           // 解析为 docIds 后与 docIds 合并
        public boolean disableRerank;
        public boolean includeDisabled;
        /** P004-B F004：触发时的 CS Agent，用于 FAQ 分组 / 文档白名单过滤；null = 无过滤。 */
        public Long csAgentId;

        public static Builder builder(String query) { return new Builder(query); }

        /** 合并 tags 解析后的 docIds；若两者都指定则取并集，保持"用户明示"的语义。 */
        public Collection<Long> effectiveDocIds(KbDocumentRepository docRepo, Long tenantId) {
            Set<Long> out = new LinkedHashSet<>();
            if (docIds != null) out.addAll(docIds);
            if (tags != null && !tags.isEmpty()) {
                List<KbDocument> byTag = docRepo.findByTenantIdAndAnyTag(tenantId, tags.toArray(new String[0]));
                for (KbDocument d : byTag) out.add(d.getId());
            }
            return out;
        }

        public static class Builder {
            private final SearchRequest r = new SearchRequest();
            public Builder(String q) { r.query = q; }
            public Builder topK(int v) { r.topK = v; return this; }
            public Builder threshold(double v) { r.threshold = v; return this; }
            public Builder docIds(List<Long> v) { r.docIds = v; return this; }
            public Builder tags(List<String> v) { r.tags = v; return this; }
            public Builder disableRerank(boolean v) { r.disableRerank = v; return this; }
            public Builder includeDisabled(boolean v) { r.includeDisabled = v; return this; }
            public Builder csAgentId(Long v) { r.csAgentId = v; return this; }
            public SearchRequest build() { return r; }
        }
    }

    public static class SearchOutcome {
        public RetrievalResult.FaqHit faqHit;
        public List<Scored> candidates = List.of();
        public boolean hasResult;
        public double topConfidence;
        public int topK;
        public double threshold;
        public boolean rerankUsed;
        public String rerankStatus;        // ok / failed / disabled / not_configured
        public String rerankProvider;
        public String rerankModel;
        public String errorMsg;

        public RetrievalResult toRetrievalResult() {
            RetrievalResult r = new RetrievalResult();
            r.setFaqHit(faqHit);
            r.setTopConfidence(topConfidence);
            r.setHasResult(hasResult);
            if (candidates != null && !candidates.isEmpty()) {
                List<RetrievalResult.Reference> refs = new ArrayList<>();
                for (int i = 0; i < Math.min(DEFAULT_REF_LIMIT, candidates.size()); i++) {
                    Scored s = candidates.get(i);
                    refs.add(new RetrievalResult.Reference(
                            s.chunk.getId(),
                            s.doc == null ? 0 : s.doc.getId(),
                            s.finalScore,
                            s.chunk.getContent(),
                            s.doc == null ? "knowledge" : s.doc.getTitle()));
                }
                r.setReferences(refs);
            }
            return r;
        }
    }

    public static class Scored {
        public MilvusVectorStore.SearchHit hit;
        public KbChunk chunk;
        public KbDocument doc;
        public double rawScore;
        public double sourceWeight;
        public Double rerankScore;    // null 表示未经过精排（未配置 / 禁用 / 失败回退）
        public double finalScore;
    }

    public static class DebugCandidate {
        public Long chunkId;
        public Long docId;
        public String docTitle;
        public List<String> docTags;
        public String content;
        public Boolean enabled;
        public double rawScore;
        public double sourceWeight;
        public Double rerankScore;
        public double finalScore;
    }

    public static class DebugResult {
        public RetrievalResult.FaqHit faqHit;
        public List<DebugCandidate> candidates;
        public int topK;
        public double threshold;
        public long elapsedMs;
        public String errorMsg;
        public boolean rerankUsed;
        public boolean disableRerank;
        public String rerankStatus;
        public String rerankProvider;
        public String rerankModel;
    }
}
