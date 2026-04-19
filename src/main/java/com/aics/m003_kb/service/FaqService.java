package com.aics.m003_kb.service;

import com.aics.common.BizException;
import com.aics.common.JsonUtil;
import com.aics.common.tenant.TenantContext;
import com.aics.m002_dialog.dto.RetrievalResult;
import com.aics.m003_kb.domain.KbFaq;
import com.aics.m003_kb.domain.KbFaqGroup;
import com.aics.m003_kb.domain.KbFaqGroupRepository;
import com.aics.m003_kb.domain.KbFaqRepository;
import com.aics.m005_admin.llm.LlmClientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FaqService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    /** 语义匹配命中阈值：cosine 相似度 ≥ 此值才认为语义命中。*/
    private static final double SEMANTIC_THRESHOLD = 0.82;

    private final KbFaqRepository repo;
    private final KbFaqGroupRepository groupRepo;
    private final StringRedisTemplate redis;
    /** LlmClientResolver 只在语义匹配/reindex 时用到；未配置 embedding 时可缺省。*/
    private final ObjectProvider<LlmClientResolver> llmResolverProvider;

    /** 租户 -> FAQ 向量索引；CRUD 时 evict，match 时懒加载。*/
    private final Map<Long, List<FaqVec>> vectorCache = new ConcurrentHashMap<>();

    private static String cacheKey(Long tenantId) { return "faq:t" + tenantId; }

    public List<KbFaq> allEnabledCached() {
        Long tenantId = TenantContext.require();
        String key = cacheKey(tenantId);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                List<Map<String, Object>> arr = JsonUtil.fromJson(cached, List.class);
                List<KbFaq> out = new ArrayList<>();
                for (Map<String, Object> m : arr) {
                    KbFaq f = new KbFaq();
                    f.setId(((Number) m.get("id")).longValue());
                    f.setTenantId(tenantId);
                    Object gid = m.get("groupId");
                    if (gid instanceof Number n) f.setGroupId(n.longValue());
                    f.setQuestion((String) m.get("question"));
                    f.setAnswer((String) m.get("answer"));
                    f.setKeywords((String) m.get("keywords"));
                    f.setEnabled((Boolean) m.getOrDefault("enabled", true));
                    out.add(f);
                }
                return out;
            } catch (Exception e) {
                log.warn("faq cache parse failed, reload: {}", e.getMessage());
            }
        }
        List<KbFaq> all = repo.findByTenantIdAndEnabledTrue(tenantId);
        List<Map<String, Object>> simplified = new ArrayList<>();
        for (KbFaq f : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("groupId", f.getGroupId());
            m.put("question", f.getQuestion());
            m.put("answer", f.getAnswer());
            m.put("keywords", f.getKeywords());
            m.put("enabled", f.getEnabled());
            simplified.add(m);
        }
        redis.opsForValue().set(key, JsonUtil.toJson(simplified), CACHE_TTL);
        return all;
    }

    public void evictCache() {
        Long tenantId = TenantContext.require();
        redis.delete(cacheKey(tenantId));
        vectorCache.remove(tenantId);
    }

    /** F001：FAQ 精确匹配 → 语义匹配 → 关键词匹配。*/
    public RetrievalResult.FaqHit match(String query) {
        return match(query, null);
    }

    /**
     * F001 + P004-B F004：允许指定 allowedGroupIds 对 FAQ 进行白名单过滤。
     * 传 null = 无限制（即旧行为）；传空集合 = 命中零（该 Agent 明确不允许任何 FAQ）。
     */
    public RetrievalResult.FaqHit match(String query, Set<Long> allowedGroupIds) {
        if (query == null) return null;
        if (allowedGroupIds != null && allowedGroupIds.isEmpty()) return null;
        String norm = normalize(query);
        List<KbFaq> faqs = filterByGroups(allEnabledCached(), allowedGroupIds);

        for (KbFaq f : faqs) {
            if (normalize(f.getQuestion()).equals(norm)) {
                return new RetrievalResult.FaqHit(f.getId(), f.getQuestion(), f.getAnswer(), 1.0, "exact");
            }
        }

        RetrievalResult.FaqHit semantic = matchSemantic(query, allowedGroupIds);
        if (semantic != null) return semantic;

        for (KbFaq f : faqs) {
            if (f.getKeywords() == null || f.getKeywords().isBlank()) continue;
            String[] ks = f.getKeywords().split("[,，]");
            int hit = 0;
            for (String k : ks) {
                String tk = k.trim();
                if (!tk.isEmpty() && norm.contains(tk)) hit++;
            }
            if (hit >= 2) {
                double conf = Math.min(1.0, 0.85 + 0.03 * hit);
                return new RetrievalResult.FaqHit(f.getId(), f.getQuestion(), f.getAnswer(), conf, "keyword");
            }
        }
        return null;
    }

    private static List<KbFaq> filterByGroups(List<KbFaq> faqs, Set<Long> allowedGroupIds) {
        if (allowedGroupIds == null) return faqs;
        List<KbFaq> out = new ArrayList<>(faqs.size());
        for (KbFaq f : faqs) {
            if (f.getGroupId() != null && allowedGroupIds.contains(f.getGroupId())) out.add(f);
        }
        return out;
    }

    private RetrievalResult.FaqHit matchSemantic(String query, Set<Long> allowedGroupIds) {
        LlmClientResolver llmResolver = llmResolverProvider.getIfAvailable();
        if (llmResolver == null) return null;

        Long tenantId;
        try {
            tenantId = TenantContext.require();
        } catch (Exception e) {
            return null;
        }

        List<FaqVec> vectors = loadVectors(tenantId);
        if (vectors.isEmpty()) return null;

        float[] qv;
        try {
            qv = llmResolver.embedOne(tenantId, query);
        } catch (Exception e) {
            log.warn("semantic faq match: embed query failed, skip semantic path: {}", e.toString());
            return null;
        }
        if (qv == null || qv.length == 0) return null;

        double qNorm = norm(qv);
        if (qNorm == 0) return null;

        FaqVec best = null;
        double bestScore = -1;
        for (FaqVec v : vectors) {
            if (v.vector.length != qv.length) continue;
            if (allowedGroupIds != null && (v.groupId == null || !allowedGroupIds.contains(v.groupId))) continue;
            double score = dot(qv, v.vector) / (qNorm * v.norm);
            if (score > bestScore) {
                bestScore = score;
                best = v;
            }
        }
        if (best == null || bestScore < SEMANTIC_THRESHOLD) return null;
        return new RetrievalResult.FaqHit(best.faqId, best.question, best.answer, bestScore, "semantic");
    }

    private List<FaqVec> loadVectors(Long tenantId) {
        return vectorCache.computeIfAbsent(tenantId, tid -> {
            List<KbFaq> enabled = repo.findByTenantIdAndEnabledTrue(tid);
            List<FaqVec> out = new ArrayList<>();
            for (KbFaq f : enabled) {
                FaqVec v = toVec(f);
                if (v != null) out.add(v);
            }
            return out;
        });
    }

    private static FaqVec toVec(KbFaq f) {
        List<Double> v = f.getQuestionVector();
        if (v == null || v.isEmpty()) return null;
        float[] arr = new float[v.size()];
        for (int i = 0; i < v.size(); i++) arr[i] = v.get(i).floatValue();
        double n = norm(arr);
        if (n == 0) return null;
        return new FaqVec(f.getId(), f.getGroupId(), f.getQuestion(), f.getAnswer(), arr, n);
    }

    @Transactional
    public KbFaq create(KbFaq req, Long userId) {
        Long tenantId = TenantContext.require();
        validate(req);
        req.setId(null);
        req.setTenantId(tenantId);
        req.setGroupId(resolveGroupId(req.getGroupId(), tenantId));
        req.setCreatedBy(userId);
        req.setUpdatedAt(OffsetDateTime.now());
        if (req.getEnabled() == null) req.setEnabled(true);
        embedQuestion(req);
        KbFaq saved = repo.save(req);
        evictCache();
        return saved;
    }

    @Transactional
    public KbFaq update(Long id, KbFaq req) {
        Long tenantId = TenantContext.require();
        KbFaq f = repo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> BizException.notFound("FAQ 不存在"));
        boolean questionChanged = !Objects.equals(f.getQuestion(), req.getQuestion());
        f.setQuestion(req.getQuestion());
        f.setAnswer(req.getAnswer());
        f.setKeywords(req.getKeywords());
        if (req.getEnabled() != null) f.setEnabled(req.getEnabled());
        if (req.getGroupId() != null && !req.getGroupId().equals(f.getGroupId())) {
            assertGroupOwned(req.getGroupId(), tenantId);
            f.setGroupId(req.getGroupId());
        }
        f.setUpdatedAt(OffsetDateTime.now());
        validate(f);
        if (questionChanged) embedQuestion(f);
        KbFaq saved = repo.save(f);
        evictCache();
        return saved;
    }

    /** 创建时：如果未传 groupId，自动挂到租户「默认分组」；否则校验归属。 */
    private Long resolveGroupId(Long requested, Long tenantId) {
        if (requested != null) {
            assertGroupOwned(requested, tenantId);
            return requested;
        }
        return groupRepo.findByTenantIdAndName(tenantId, "默认分组")
                .map(KbFaqGroup::getId)
                .orElseGet(() -> {
                    KbFaqGroup g = new KbFaqGroup();
                    g.setTenantId(tenantId);
                    g.setName("默认分组");
                    g.setDescription("系统自动创建的默认分组");
                    g.setSortOrder(0);
                    g.setUpdatedAt(OffsetDateTime.now());
                    return groupRepo.save(g).getId();
                });
    }

    private void assertGroupOwned(Long groupId, Long tenantId) {
        groupRepo.findByIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> BizException.of("groupId 非法或不属于当前租户"));
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = TenantContext.require();
        repo.findByIdAndTenantId(id, tenantId).ifPresent(f -> {
            repo.delete(f);
            evictCache();
        });
    }

    /** 管理后台：全量重算当前租户已启用 FAQ 的问题向量。*/
    @Transactional
    public ReindexResult reindexAll() {
        Long tenantId = TenantContext.require();
        LlmClientResolver llmResolver = llmResolverProvider.getIfAvailable();
        if (llmResolver == null) {
            throw BizException.of("embedding 服务未配置，无法 reindex");
        }
        List<KbFaq> all = repo.findByTenantIdAndEnabledTrue(tenantId);
        int ok = 0, fail = 0;
        for (KbFaq f : all) {
            try {
                float[] v = llmResolver.embedOne(tenantId, f.getQuestion());
                if (v == null || v.length == 0) { fail++; continue; }
                f.setQuestionVector(toDoubleList(v));
                f.setQuestionVectorDim(v.length);
                f.setQuestionVectorUpdatedAt(OffsetDateTime.now());
                repo.save(f);
                ok++;
            } catch (Exception e) {
                log.warn("reindex faq {} failed: {}", f.getId(), e.getMessage());
                fail++;
            }
        }
        evictCache();
        return new ReindexResult(all.size(), ok, fail);
    }

    /** 最佳努力：embedding 失败不阻塞写入，仅降级为 exact/keyword 匹配。*/
    private void embedQuestion(KbFaq f) {
        LlmClientResolver llmResolver = llmResolverProvider.getIfAvailable();
        if (llmResolver == null) return;
        try {
            Long tenantId = TenantContext.require();
            float[] v = llmResolver.embedOne(tenantId, f.getQuestion());
            if (v == null || v.length == 0) return;
            f.setQuestionVector(toDoubleList(v));
            f.setQuestionVectorDim(v.length);
            f.setQuestionVectorUpdatedAt(OffsetDateTime.now());
        } catch (Exception e) {
            log.warn("embed faq question failed (will fallback to exact/keyword): {}", e.getMessage());
            f.setQuestionVector(null);
            f.setQuestionVectorDim(null);
            f.setQuestionVectorUpdatedAt(null);
        }
    }

    private static List<Double> toDoubleList(float[] v) {
        List<Double> out = new ArrayList<>(v.length);
        for (float x : v) out.add((double) x);
        return out;
    }

    private static double norm(float[] v) {
        double s = 0;
        for (float x : v) s += x * x;
        return Math.sqrt(s);
    }

    private static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private void validate(KbFaq f) {
        if (f.getQuestion() == null || f.getQuestion().length() < 1 || f.getQuestion().length() > 512)
            throw BizException.of("question 长度 1~512");
        if (f.getAnswer() == null || f.getAnswer().length() < 1 || f.getAnswer().length() > 2000)
            throw BizException.of("answer 长度 1~2000");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s　]+", "")
                .replaceAll("[，,。.！!？?；;：:]", "");
    }

    private record FaqVec(Long faqId, Long groupId, String question, String answer, float[] vector, double norm) {}

    public record ReindexResult(int total, int ok, int failed) {}
}
