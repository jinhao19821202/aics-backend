package com.aics.m005_admin.tenant;

import com.aics.config.AppProperties;
import com.aics.infra.milvus.MilvusVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 按租户管理 Milvus collection：创建 aics_kb_t{id} + HNSW index + load。
 * 在租户创建时 provision，embedding 维度变更时 recreate（由重建索引流程调用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionProvisioner {

    private final MilvusVectorStore milvus;
    private final TenantRepository tenantRepo;
    private final AppProperties props;

    /** 为租户创建 collection（若不存在）。返回实际使用的 collection 名。*/
    public String provision(Tenant t) {
        String name = ensureName(t);
        int dim = effectiveDim(t);
        try {
            if (milvus.hasCollection(name)) {
                milvus.loadCollection(name);
                return name;
            }
            milvus.createCollection(name, dim);
            milvus.createIndex(name);
            milvus.loadCollection(name);
            if (!name.equals(t.getMilvusCollection()) || !Integer.valueOf(dim).equals(t.getEmbeddingDim())) {
                t.setMilvusCollection(name);
                t.setEmbeddingDim(dim);
                tenantRepo.save(t);
            }
            log.info("milvus collection provisioned tenant={} name={} dim={}", t.getId(), name, dim);
            return name;
        } catch (Exception e) {
            log.error("provision milvus collection failed tenant={}", t.getId(), e);
            throw new RuntimeException("provision failed: " + e.getMessage(), e);
        }
    }

    /** 删除租户 collection（用于停用租户 / 重建前清理）。*/
    public void drop(Tenant t) {
        String name = ensureName(t);
        try {
            milvus.dropCollection(name);
            log.info("milvus collection dropped tenant={} name={}", t.getId(), name);
        } catch (Exception e) {
            log.warn("drop milvus collection failed tenant={}: {}", t.getId(), e.getMessage());
        }
    }

    /** 重建：drop 然后 provision（dim 可能变化）。*/
    public String recreate(Tenant t, Integer newDim) {
        if (newDim != null) t.setEmbeddingDim(newDim);
        drop(t);
        return provision(t);
    }

    /** 取租户当前 collection 名；兜底按 aics_kb_t{id} 计算并落库。*/
    public String collectionFor(Long tenantId) {
        Tenant t = tenantRepo.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("tenant not found: " + tenantId));
        return ensureName(t);
    }

    private String ensureName(Tenant t) {
        if (t.getMilvusCollection() == null || t.getMilvusCollection().isBlank()) {
            String name = MilvusVectorStore.tenantCollection(t.getId());
            t.setMilvusCollection(name);
        }
        return t.getMilvusCollection();
    }

    private int effectiveDim(Tenant t) {
        if (t.getEmbeddingDim() != null && t.getEmbeddingDim() > 0) return t.getEmbeddingDim();
        return props.getDashscope().getEmbeddingDim();
    }
}
