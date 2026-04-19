package com.aics.m005_admin.tenant;

import com.aics.common.BizException;
import com.aics.common.tenant.TenantContext;
import com.aics.infra.milvus.MilvusVectorStore;
import com.aics.m003_kb.domain.KbChunk;
import com.aics.m003_kb.domain.KbChunkRepository;
import com.aics.m005_admin.llm.LlmClientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户知识库重建：drop 旧 collection → 按新 embedding_dim 重建 → 扫 kb_chunk 重新 embed + insert。
 * 触发场景：embedding model 或 dim 变更 / 运维手动触发。失败写 tenant.reindex_last_error。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexService {

    private static final int PAGE_SIZE = 128;
    private static final int EMBED_BATCH = 16;

    private final TenantRepository tenantRepo;
    private final CollectionProvisioner provisioner;
    private final LlmClientResolver llm;
    private final MilvusVectorStore milvus;
    private final KbChunkRepository chunkRepo;

    @Transactional
    public void markRunning(Long tenantId) {
        Tenant t = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new BizException(404, "tenant not found: " + tenantId));
        if ("running".equals(t.getReindexStatus())) {
            throw new BizException(409, "reindex already running");
        }
        t.setReindexStatus("running");
        t.setReindexLastError(null);
        tenantRepo.save(t);
    }

    /** 由管理员触发：先 markRunning，再异步执行。*/
    @Async("kbParserExecutor")
    public void triggerAsync(Long tenantId, Integer newDim) {
        TenantContext.set(tenantId);
        try {
            doReindex(tenantId, newDim);
        } finally {
            TenantContext.clear();
        }
    }

    /** 同步执行重建，供测试和管理端内部调用。*/
    public void doReindex(Long tenantId, Integer newDim) {
        Tenant t;
        try {
            t = tenantRepo.findById(tenantId)
                    .orElseThrow(() -> new BizException(404, "tenant not found"));
            String collection = provisioner.recreate(t, newDim);
            log.info("reindex started tenant={} collection={}", tenantId, collection);

            int page = 0;
            long total = 0;
            while (true) {
                Page<KbChunk> pg = chunkRepo.findByTenantIdAndEnabledTrueOrderByIdAsc(
                        tenantId, PageRequest.of(page, PAGE_SIZE));
                if (pg.isEmpty()) break;

                for (int i = 0; i < pg.getNumberOfElements(); i += EMBED_BATCH) {
                    List<KbChunk> slice = pg.getContent().subList(i, Math.min(i + EMBED_BATCH, pg.getNumberOfElements()));
                    List<String> texts = slice.stream().map(KbChunk::getContent).toList();
                    List<float[]> vectors = llm.embed(tenantId, texts);
                    List<Long> chunkIds = slice.stream().map(KbChunk::getId).toList();
                    List<Long> docIds = slice.stream().map(KbChunk::getDocId).toList();

                    List<Long> milvusIds = milvus.insertBatch(collection, chunkIds, docIds, vectors, true);
                    for (int k = 0; k < slice.size(); k++) {
                        slice.get(k).setMilvusId(milvusIds.get(k));
                    }
                    chunkRepo.saveAll(slice);
                    total += slice.size();
                }
                if (!pg.hasNext()) break;
                page++;
            }
            markDone(tenantId, total, null);
            log.info("reindex done tenant={} chunks={}", tenantId, total);
        } catch (Exception e) {
            log.error("reindex failed tenant={}", tenantId, e);
            markDone(tenantId, 0, e.getMessage());
        }
    }

    @Transactional
    public void markDone(Long tenantId, long total, String errorMsg) {
        tenantRepo.findById(tenantId).ifPresent(t -> {
            t.setReindexStatus(errorMsg == null ? "idle" : "error");
            t.setReindexLastError(errorMsg == null ? null : trunc(errorMsg, 400));
            tenantRepo.save(t);
        });
    }

    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public ReindexStatus status(Long tenantId) {
        Tenant t = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new BizException(404, "tenant not found"));
        return new ReindexStatus(t.getReindexStatus(), t.getReindexLastError());
    }

    public record ReindexStatus(String status, String lastError) {}
}
