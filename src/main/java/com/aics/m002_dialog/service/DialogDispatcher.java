package com.aics.m002_dialog.service;

import com.aics.common.tenant.TenantContext;
import com.aics.config.AppProperties;
import com.aics.infra.dashscope.DashScopeClient;
import com.aics.infra.wecom.WeComApiClient;
import com.aics.m001_message.dto.ContextSnapshot;
import com.aics.m001_message.dto.InboundEnvelope;
import com.aics.m001_message.service.ContextWindowService;
import com.aics.m002_dialog.dto.RetrievalResult;
import com.aics.m003_kb.service.KnowledgeSearchService;
import com.aics.m004_handoff.service.HandoffService;
import com.aics.m005_admin.audit.LlmInvocationService;
import com.aics.m005_admin.llm.LlmClientResolver;
import com.aics.m005_admin.sensitive.SensitiveWordService;
import com.aics.m005_admin.sensitive.SensitiveWordService.CheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * M002 对话引擎入口：RAG → LLM → 发送 / 兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DialogDispatcher {

    private final AppProperties props;
    private final QueryRewriter rewriter;
    private final KnowledgeSearchService kb;
    private final PromptBuilder promptBuilder;
    private final LlmClientResolver llm;
    private final ConfidenceJudge judge;
    private final HandoffService handoff;
    private final SensitiveWordService sensitive;
    private final LlmInvocationService audit;
    private final ContextWindowService ctx;
    private final WeComApiClient wecom;

    /** 非 @bot 无效：只回引导话术。 */
    public void sendGuide(String groupId) {
        String msg = "您好，请告诉我您的具体问题～";
        wecom.sendTextToGroup(groupId, msg);
        ctx.onTextMessage(groupId, "bot", props.getBotName(), syntheticMsgId(), msg, true);
    }

    public void sendBlockedAndHandoff(InboundEnvelope env) {
        String fallback = "您好，您的问题我们需要人工客服进一步处理，马上为您转接～";
        wecom.sendTextToGroup(env.getGroupId(), fallback);
        handoff.trigger(env.getGroupId(), "SENSITIVE_OUTPUT", env.getContent(), env.getConversationId());
    }

    public void dispatch(ContextSnapshot snap) {
        long started = System.currentTimeMillis();
        String query = snap.getTriggerMsg().getText();

        // 用户显式请求转人工 → 兜底
        if (judge.isExplicitUserRequest(query)) {
            handoff.trigger(snap.getGroupId(), "USER_REQUEST", query, snap.getConversationId());
            return;
        }

        // 1. 问题改写
        String rewritten = rewriter.rewriteIfNeeded(snap);

        // 2. RAG 检索（P004-B F004：带 Agent 内容白名单过滤）
        RetrievalResult rag = kb.search(rewritten, snap.getCsAgentId());

        // 2.1 FAQ 精确命中 → 直接回复
        if (rag.getFaqHit() != null && rag.getFaqHit().getConfidence() >= 0.95) {
            String ans = rag.getFaqHit().getAnswer() + "\n（如有疑问可回复\"转人工\"）";
            CheckResult out = sensitive.checkOutbound(snap.getGroupId(), ans);
            if (out.isBlocked()) {
                handoff.trigger(snap.getGroupId(), "SENSITIVE_OUTPUT", rewritten, snap.getConversationId());
                return;
            }
            wecom.sendTextToGroup(snap.getGroupId(), out.getProcessedText());
            ctx.onTextMessage(snap.getGroupId(), "bot", props.getBotName(), syntheticMsgId(), out.getProcessedText(), true);
            audit.recordFaqHit(snap, rag, out.getProcessedText(), System.currentTimeMillis() - started);
            judge.resetLowConfidence(snap.getGroupId());
            return;
        }

        // 2.2 RAG 结果过弱 → 兜底
        if (judge.isRagTooWeak(rag)) {
            log.info("low confidence rag, trigger handoff: group={}, topScore={}",
                    snap.getGroupId(), rag == null ? 0 : rag.getTopConfidence());
            if (judge.incrementLowConfidenceAndCheck(snap.getGroupId())) {
                handoff.trigger(snap.getGroupId(), "REPEATED_LOWCONF", rewritten, snap.getConversationId());
            } else {
                handoff.trigger(snap.getGroupId(), "LOW_CONFIDENCE", rewritten, snap.getConversationId());
            }
            return;
        }

        // 3. 组装 Prompt 调用 LLM
        PromptBuilder.Prompts prompts = promptBuilder.build(snap, rag);
        String model = chooseModel(prompts);

        DashScopeClient.ChatResult chat;
        try {
            chat = llm.chat(TenantContext.require(), model, prompts.system(), prompts.user(), 0.3, 800);
        } catch (Exception e) {
            log.error("qwen call failed, handoff: {}", e.getMessage());
            handoff.trigger(snap.getGroupId(), "LLM_FAIL", rewritten, snap.getConversationId());
            audit.recordFailure(snap, prompts, model, e.getMessage(), System.currentTimeMillis() - started);
            return;
        }

        if (chat == null || chat.text == null || chat.text.isBlank()) {
            handoff.trigger(snap.getGroupId(), "CIRCUIT_OPEN", rewritten, snap.getConversationId());
            audit.recordFailure(snap, prompts, model, "empty reply / circuit breaker", System.currentTimeMillis() - started);
            return;
        }

        // 4. LLM 输出兜底短语 → 转人工
        if (judge.llmRepliesHandoffPhrase(chat.text)) {
            handoff.trigger(snap.getGroupId(), "LOW_CONFIDENCE", rewritten, snap.getConversationId());
            audit.recordHandoff(snap, prompts, chat, rag, System.currentTimeMillis() - started);
            return;
        }

        // 5. 输出敏感词校验
        CheckResult out = sensitive.checkOutbound(snap.getGroupId(), chat.text);
        if (out.isBlocked()) {
            handoff.trigger(snap.getGroupId(), "SENSITIVE_OUTPUT", rewritten, snap.getConversationId());
            audit.recordHandoff(snap, prompts, chat, rag, System.currentTimeMillis() - started);
            return;
        }

        // 6. 发送答复
        String reply = out.getProcessedText() + "\n（如有疑问可回复\"转人工\"）";
        wecom.sendTextToGroup(snap.getGroupId(), reply);
        ctx.onTextMessage(snap.getGroupId(), "bot", props.getBotName(), syntheticMsgId(), reply, true);
        audit.recordSuccess(snap, prompts, chat, rag, reply, System.currentTimeMillis() - started);
        judge.resetLowConfidence(snap.getGroupId());
    }

    private String chooseModel(PromptBuilder.Prompts p) {
        int approxTokens = (p.system().length() + p.user().length()) / 2;
        if (approxTokens > 5000) return props.getDashscope().getLargeModel();
        return props.getDashscope().getChatModel();
    }

    private String syntheticMsgId() {
        return "bot_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
