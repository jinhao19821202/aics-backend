package com.aics.infra.milvus;

import com.aics.config.AppProperties;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Milvus 向量库封装：collection 生命周期 + 多租户 insert/search/delete。
 * 所有读写方法要求传入 collection 名；由 CollectionProvisioner 统一按租户提供 aics_kb_t{id}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusVectorStore {

    public static final String F_ID = "id";
    public static final String F_EMB = "embedding";
    public static final String F_DOC = "doc_id";
    public static final String F_ENABLED = "enabled";
    public static final String F_CHUNK_ID = "chunk_id";

    private final MilvusServiceClient client;
    private final AppProperties props;

    public static String tenantCollection(Long tenantId) {
        return "aics_kb_t" + tenantId;
    }

    // ----------- collection 生命周期 -----------

    public boolean hasCollection(String collection) {
        R<Boolean> has = client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collection).build());
        return Boolean.TRUE.equals(has.getData());
    }

    public void createCollection(String collection, int embeddingDim) {
        FieldType fId = FieldType.newBuilder().withName(F_ID).withDataType(DataType.Int64)
                .withPrimaryKey(true).withAutoID(true).build();
        FieldType fEmb = FieldType.newBuilder().withName(F_EMB).withDataType(DataType.FloatVector)
                .withDimension(embeddingDim).build();
        FieldType fDoc = FieldType.newBuilder().withName(F_DOC).withDataType(DataType.Int64).build();
        FieldType fChunk = FieldType.newBuilder().withName(F_CHUNK_ID).withDataType(DataType.Int64).build();
        FieldType fEnabled = FieldType.newBuilder().withName(F_ENABLED).withDataType(DataType.Bool).build();

        R<io.milvus.param.RpcStatus> r = client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(collection)
                .withDescription("knowledge base chunks for tenant-specific collection")
                .addFieldType(fId).addFieldType(fEmb).addFieldType(fDoc).addFieldType(fChunk).addFieldType(fEnabled)
                .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                .build());
        if (r.getStatus() != 0) {
            throw new RuntimeException("milvus createCollection failed: " + r.getMessage());
        }
    }

    public void createIndex(String collection) {
        client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(collection)
                .withFieldName(F_EMB)
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.IP)
                .withExtraParam("{\"M\": 16, \"efConstruction\": 200}")
                .withSyncMode(false)
                .build());
    }

    public void loadCollection(String collection) {
        client.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collection).build());
    }

    public void dropCollection(String collection) {
        R<io.milvus.param.RpcStatus> r = client.dropCollection(DropCollectionParam.newBuilder()
                .withCollectionName(collection).build());
        if (r.getStatus() != 0 && r.getStatus() != 1) { // 1 = not found (OK)
            log.warn("milvus drop collection failed: {}", r.getMessage());
        }
    }

    // ----------- 数据读写（多租户）-----------

    public List<Long> insertBatch(String collection, List<Long> chunkIds, List<Long> docIds,
                                  List<float[]> embeddings, boolean enabled) {
        if (chunkIds.isEmpty()) return Collections.emptyList();
        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(F_EMB, toList(embeddings)));
        fields.add(new InsertParam.Field(F_DOC, docIds));
        fields.add(new InsertParam.Field(F_CHUNK_ID, chunkIds));
        List<Boolean> enableds = new ArrayList<>();
        for (int i = 0; i < chunkIds.size(); i++) enableds.add(enabled);
        fields.add(new InsertParam.Field(F_ENABLED, enableds));

        R<MutationResult> result = client.insert(InsertParam.newBuilder()
                .withCollectionName(collection)
                .withFields(fields)
                .build());
        if (result.getStatus() != 0) {
            throw new RuntimeException("milvus insert failed: " + result.getMessage());
        }
        return List.copyOf(result.getData().getIDs().getIntId().getDataList());
    }

    public void deleteByDoc(String collection, long docId) {
        client.delete(DeleteParam.newBuilder()
                .withCollectionName(collection)
                .withExpr(F_DOC + " == " + docId)
                .build());
    }

    public List<SearchHit> search(String collection, float[] queryVec, int topK, Float scoreThreshold, String extraExpr) {
        String expr = F_ENABLED + " == true";
        if (extraExpr != null && !extraExpr.isBlank()) expr += " && " + extraExpr;

        SearchParam param = SearchParam.newBuilder()
                .withCollectionName(collection)
                .withMetricType(MetricType.IP)
                .withOutFields(List.of(F_CHUNK_ID, F_DOC))
                .withTopK(topK)
                .withVectors(List.of(toFloatList(queryVec)))
                .withVectorFieldName(F_EMB)
                .withExpr(expr)
                .withParams("{\"ef\": 64}")
                .build();
        R<SearchResults> resp = client.search(param);
        if (resp.getStatus() != 0) {
            log.error("milvus search failed: {}", resp.getMessage());
            return List.of();
        }
        SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        List<SearchHit> out = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) {
            var s = scores.get(i);
            if (scoreThreshold != null && s.getScore() < scoreThreshold) continue;
            Object chunkIdObj = wrapper.getFieldData(F_CHUNK_ID, 0).get(i);
            Object docIdObj = wrapper.getFieldData(F_DOC, 0).get(i);
            SearchHit hit = new SearchHit();
            hit.milvusId = s.getLongID();
            hit.chunkId = ((Number) chunkIdObj).longValue();
            hit.docId = ((Number) docIdObj).longValue();
            hit.score = s.getScore();
            out.add(hit);
        }
        return out;
    }

    // ----------- 向后兼容（使用 app.milvus.collection，默认租户）-----------

    @Deprecated
    public List<Long> insertBatch(List<Long> chunkIds, List<Long> docIds, List<float[]> embeddings, boolean enabled) {
        return insertBatch(props.getMilvus().getCollection(), chunkIds, docIds, embeddings, enabled);
    }

    @Deprecated
    public void deleteByDoc(long docId) {
        deleteByDoc(props.getMilvus().getCollection(), docId);
    }

    @Deprecated
    public List<SearchHit> search(float[] queryVec, int topK, Float scoreThreshold, String extraExpr) {
        return search(props.getMilvus().getCollection(), queryVec, topK, scoreThreshold, extraExpr);
    }

    private static List<List<Float>> toList(List<float[]> vs) {
        List<List<Float>> r = new ArrayList<>(vs.size());
        for (float[] v : vs) r.add(toFloatList(v));
        return r;
    }

    private static List<Float> toFloatList(float[] v) {
        List<Float> r = new ArrayList<>(v.length);
        for (float x : v) r.add(x);
        return r;
    }

    public static class SearchHit {
        public long milvusId;
        public long chunkId;
        public long docId;
        public float score;
    }
}
