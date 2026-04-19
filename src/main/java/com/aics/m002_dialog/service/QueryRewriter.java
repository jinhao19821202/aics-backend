package com.aics.m002_dialog.service;

import com.aics.common.tenant.TenantContext;
import com.aics.config.AppProperties;
import com.aics.infra.dashscope.DashScopeClient;
import com.aics.m001_message.dto.ContextSnapshot;
import com.aics.m005_admin.llm.LlmClientResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriter {

    private static final Pattern REFERENCE = Pattern.compile("它|这个|那个|上面|刚才|之前|上述");

    private final LlmClientResolver llm;
    private final AppProperties props;

    public String rewriteIfNeeded(ContextSnapshot snap) {
        String q = snap.getTriggerMsg().getText();
        if (q == null) return q;

        boolean hasReference = REFERENCE.matcher(q).find();
        boolean isShort = q.length() <= 6;
        if (!hasReference && !isShort) return q;

        List<ContextSnapshot.HistoryItem> hist = snap.getHistory();
        if (hist.isEmpty()) return q;

        int take = Math.min(3, hist.size());
        List<ContextSnapshot.HistoryItem> recent = hist.subList(hist.size() - take, hist.size());

        StringBuilder sb = new StringBuilder();
        for (ContextSnapshot.HistoryItem h : recent) {
            sb.append(h.isBot() ? "客服: " : "客户: ").append(h.getText()).append("\n");
        }

        String sys = "你是一个查询改写助手。请基于对话历史，把原问题改写成一个脱离上下文也能理解的独立完整查询，不要增加新信息，只输出改写后的一句话。";
        String user = "历史对话：\n" + sb + "\n原问题：" + q + "\n改写后：";

        try {
            DashScopeClient.ChatResult r = llm.chat(TenantContext.require(), props.getDashscope().getLiteModel(), sys, user, 0.0, 120);
            if (r.text == null || r.text.isBlank()) return q;
            return r.text.trim();
        } catch (Exception e) {
            log.warn("query rewrite failed, fall back to original: {}", e.getMessage());
            return q;
        }
    }
}
