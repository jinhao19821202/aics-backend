package com.aics.m003_kb.service;

import com.aics.common.BizException;
import com.aics.common.tenant.TenantContext;
import com.aics.infra.milvus.MilvusVectorStore;
import com.aics.infra.storage.ObjectStorage;
import com.aics.m003_kb.domain.KbChunk;
import com.aics.m003_kb.domain.KbChunkRepository;
import com.aics.m003_kb.domain.KbDocument;
import com.aics.m003_kb.domain.KbDocumentRepository;
import com.aics.m005_admin.llm.LlmClientResolver;
import com.aics.m005_admin.tenant.CollectionProvisioner;
import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * M003 F001 + F002：上传、解析、切片、向量化、入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIndexService {

    private final KbDocumentRepository docRepo;
    private final KbChunkRepository chunkRepo;
    private final DocumentParser parser;
    private final TextSplitter splitter;
    private final LlmClientResolver llm;
    private final MilvusVectorStore milvus;
    private final ObjectStorage storage;
    private final CollectionProvisioner collectionProvisioner;

    @Transactional
    public KbDocument uploadDocument(MultipartFile file, String title, List<String> tags, Long uploaderId) {
        Long tenantId = TenantContext.require();
        try {
            byte[] bytes = file.getBytes();
            String hash = Hashing.sha256().hashBytes(bytes).toString();

            Optional<KbDocument> exist = docRepo.findByTenantIdAndFileHashAndDeletedFalse(tenantId, hash);
            if (exist.isPresent()) {
                throw BizException.conflict("相同内容的文档已存在: id=" + exist.get().getId());
            }

            String objectName = "t/" + tenantId + "/kb/" + hash + "-" + file.getOriginalFilename();
            storage.putObject(objectName, file.getInputStream(), bytes.length, file.getContentType());

            KbDocument doc = new KbDocument();
            doc.setTenantId(tenantId);
            doc.setTitle(title == null || title.isBlank() ? file.getOriginalFilename() : title);
            doc.setSourceType("document");
            doc.setFileUrl(objectName);
            doc.setFileHash(hash);
            doc.setStatus("parsing");
            doc.setTags(tags == null ? List.of() : tags);
            doc.setCreatedBy(uploaderId);
            doc.setUpdatedAt(OffsetDateTime.now());
            doc = docRepo.save(doc);

            asyncParseAndIndex(tenantId, doc.getId(), bytes, file.getOriginalFilename());
            return doc;
        } catch (IOException e) {
            throw BizException.of("读取上传文件失败: " + e.getMessage());
        }
    }

    @Async("kbParserExecutor")
    public void asyncParseAndIndex(Long tenantId, Long docId, byte[] bytes, String filename) {
        TenantContext.set(tenantId);
        try {
            processDocument(docId, bytes, filename);
        } catch (Exception e) {
            log.error("parse+index failed doc={}", docId, e);
            fail(docId, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void processDocument(Long docId, byte[] bytes, String filename) {
        Long tenantId = TenantContext.require();
        String text = parser.parse(bytes, filename);
        List<String> chunks = splitter.split(text);
        if (chunks.isEmpty()) {
            fail(docId, "文档内容为空");
            return;
        }

        KbDocument doc = docRepo.findByIdAndTenantId(docId, tenantId).orElseThrow();

        List<KbChunk> saved = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KbChunk c = new KbChunk();
            c.setTenantId(tenantId);
            c.setDocId(docId);
            c.setContent(chunks.get(i));
            c.setEnabled(true);
            Map<String, Object> meta = new HashMap<>();
            meta.put("seq", i);
            meta.put("source", doc.getTitle());
            c.setMeta(meta);
            saved.add(chunkRepo.save(c));
        }

        String collection = collectionProvisioner.collectionFor(tenantId);
        int batch = 16;
        List<Long> allMilvusIds = new ArrayList<>();
        for (int i = 0; i < saved.size(); i += batch) {
            List<KbChunk> slice = saved.subList(i, Math.min(i + batch, saved.size()));
            List<String> texts = slice.stream().map(KbChunk::getContent).toList();
            List<float[]> embeddings = llm.embed(tenantId, texts);
            List<Long> chunkIds = slice.stream().map(KbChunk::getId).toList();
            List<Long> docIds = new ArrayList<>();
            for (int k = 0; k < slice.size(); k++) docIds.add(docId);

            List<Long> milvusIds = milvus.insertBatch(collection, chunkIds, docIds, embeddings, true);
            for (int k = 0; k < slice.size(); k++) {
                slice.get(k).setMilvusId(milvusIds.get(k));
            }
            chunkRepo.saveAll(slice);
            allMilvusIds.addAll(milvusIds);
        }

        doc.setStatus("ready");
        doc.setChunkCount(saved.size());
        doc.setUpdatedAt(OffsetDateTime.now());
        docRepo.save(doc);
        log.info("document ready: docId={}, chunks={}", docId, saved.size());
    }

    @Transactional
    public void fail(Long docId, String msg) {
        docRepo.findById(docId).ifPresent(doc -> {
            doc.setStatus("failed");
            doc.setErrorMsg(msg);
            doc.setUpdatedAt(OffsetDateTime.now());
            docRepo.save(doc);
        });
    }

    @Transactional
    public void softDelete(Long docId) {
        Long tenantId = TenantContext.require();
        KbDocument doc = docRepo.findByIdAndTenantId(docId, tenantId).orElseThrow(() -> BizException.notFound("文档不存在"));
        doc.setDeleted(true);
        doc.setUpdatedAt(OffsetDateTime.now());
        docRepo.save(doc);
        try {
            milvus.deleteByDoc(collectionProvisioner.collectionFor(tenantId), docId);
        } catch (Exception e) {
            log.warn("milvus del failed: {}", e.getMessage());
        }
    }

    @Transactional
    public KbDocument retry(Long docId) {
        Long tenantId = TenantContext.require();
        KbDocument doc = docRepo.findByIdAndTenantId(docId, tenantId).orElseThrow(() -> BizException.notFound("文档不存在"));
        if (!"failed".equals(doc.getStatus())) throw BizException.of("仅允许重试 failed 状态文档");
        doc.setStatus("parsing");
        docRepo.save(doc);
        return doc;
    }
}
