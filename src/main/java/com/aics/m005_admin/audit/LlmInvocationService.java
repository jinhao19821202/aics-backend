package com.aics.m005_admin.audit;

import com.aics.common.tenant.TenantContext;
import com.aics.infra.dashscope.DashScopeClient;
import com.aics.m001_message.dto.ContextSnapshot;
import com.aics.m002_dialog.dto.RetrievalResult;
import com.aics.m002_dialog.service.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将每一次 RAG/LLM/Handoff 调用写入 llm_invocation，用于审计与统计。
 * 失败只打日志，不抛异常，避免影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmInvocationService {

    private final LlmInvocationRepository repo;

    public void recordFaqHit(ContextSnapshot snap, RetrievalResult rag, String response, long latencyMs) {
        safeSave(() -> {
            LlmInvocation l = base(snap, "faq", "[FAQ direct hit]");
            l.setResponse(response);
            l.setLatencyMs((int) latencyMs);
            l.setReferencesUsed(refs(rag));
            l.setConfidence(conf(rag == null || rag.getFaqHit() == null ? 0 : rag.getFaqHit().getConfidence()));
            l.setHandoff(false);
            l.setStatus("OK");
            return l;
        });
    }

    public void recordSuccess(ContextSnapshot snap, PromptBuilder.Prompts prompts, DashScopeClient.ChatResult chat,
                              RetrievalResult rag, String finalReply, long latencyMs) {
        safeSave(() -> {
            LlmInvocation l = base(snap, chat.model, truncate(prompts.user(), 4000));
            l.setResponse(truncate(finalReply, 4000));
            l.setPromptTokens(chat.promptTokens);
            l.setCompletionTokens(chat.completionTokens);
            l.setLatencyMs((int) latencyMs);
            l.setReferencesUsed(refs(rag));
            l.setConfidence(conf(rag == null ? 0 : rag.getTopConfidence()));
            l.setHandoff(false);
            l.setStatus("OK");
            return l;
        });
    }

    public void recordFailure(ContextSnapshot snap, PromptBuilder.Prompts prompts, String model,
                              String errorMsg, long latencyMs) {
        safeSave(() -> {
            LlmInvocation l = base(snap, model, truncate(prompts == null ? "" : prompts.user(), 4000));
            l.setLatencyMs((int) latencyMs);
            l.setHandoff(true);
            l.setStatus("FAIL");
            l.setErrorMsg(truncate(errorMsg, 800));
            return l;
        });
    }

    public void recordHandoff(ContextSnapshot snap, PromptBuilder.Prompts prompts, DashScopeClient.ChatResult chat,
                              RetrievalResult rag, long latencyMs) {
        safeSave(() -> {
            LlmInvocation l = base(snap, chat == null ? "unknown" : chat.model,
                    truncate(prompts == null ? "" : prompts.user(), 4000));
            l.setResponse(chat == null ? null : truncate(chat.text, 4000));
            if (chat != null) {
                l.setPromptTokens(chat.promptTokens);
                l.setCompletionTokens(chat.completionTokens);
            }
            l.setLatencyMs((int) latencyMs);
            l.setReferencesUsed(refs(rag));
            l.setConfidence(conf(rag == null ? 0 : rag.getTopConfidence()));
            l.setHandoff(true);
            l.setStatus("OK");
            return l;
        });
    }

    private LlmInvocation base(ContextSnapshot snap, String model, String prompt) {
        LlmInvocation l = new LlmInvocation();
        Long tid = TenantContext.currentOrNull();
        l.setTenantId(tid != null ? tid : TenantContext.DEFAULT_TENANT_ID);
        l.setConversationId(snap.getConversationId());
        l.setGroupId(snap.getGroupId());
        l.setTriggerMsgId(snap.getTriggerMsg() == null ? null : snap.getTriggerMsg().getMsgId());
        l.setModel(model);
        l.setPrompt(prompt);
        return l;
    }

    private List<Map<String, Object>> refs(RetrievalResult rag) {
        if (rag == null || rag.getReferences() == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (RetrievalResult.Reference r : rag.getReferences()) {
            out.add(Map.of(
                    "chunkId", r.getChunkId() == null ? 0L : r.getChunkId(),
                    "docId", r.getDocId() == null ? 0L : r.getDocId(),
                    "score", r.getScore(),
                    "source", r.getSource() == null ? "" : r.getSource()));
        }
        return out;
    }

    private BigDecimal conf(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void safeSave(java.util.function.Supplier<LlmInvocation> builder) {
        try {
            repo.save(builder.get());
        } catch (Exception e) {
            log.warn("audit save failed: {}", e.getMessage());
        }
    }
}
