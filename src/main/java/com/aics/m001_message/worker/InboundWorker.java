package com.aics.m001_message.worker;

import com.aics.common.tenant.TenantContext;
import com.aics.config.AppProperties;
import com.aics.m001_message.dto.ContextSnapshot;
import com.aics.m001_message.dto.InboundEnvelope;
import com.aics.m001_message.service.ContextWindowService;
import com.aics.m002_dialog.service.DialogDispatcher;
import com.aics.m004_handoff.service.GroupSessionService;
import com.aics.m005_admin.sensitive.SensitiveWordService;
import com.aics.m005_admin.sensitive.SensitiveWordService.CheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * M001 Worker：消费 `wecom.msg.inbound`，完成：
 *   - 非文本 / 非 @ 消息：仅入上下文窗口
 *   - 文本 + @bot：走 F002 → 上下文快照 → DialogDispatcher
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboundWorker {

    private final AppProperties props;
    private final ContextWindowService ctx;
    private final DialogDispatcher dialog;
    private final GroupSessionService sessions;
    private final SensitiveWordService sensitive;

    @KafkaListener(topics = "${app.kafka-topics.inbound}", groupId = "cs-bot-worker",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(InboundEnvelope env, Acknowledgment ack) {
        MDC.put("conversationId", env.getConversationId());
        Long tenantId = env.getTenantId() != null ? env.getTenantId() : TenantContext.DEFAULT_TENANT_ID;
        TenantContext.set(tenantId);
        try {
            // 非文本：只入库流水，不进窗口、不触发
            if (!"text".equalsIgnoreCase(env.getMsgType())) {
                ctx.persistNonText(env.getGroupId(), env.getFromUserid(), env.getFromUserid(),
                        env.getMsgId(), env.getMsgType());
                ack.acknowledge();
                return;
            }

            // 入上下文窗口
            ctx.onTextMessage(env.getGroupId(), env.getFromUserid(), env.getFromUserid(),
                    env.getMsgId(), env.getContent(), false);

            // 人工接管期间：即使 @bot 也静默
            String state = sessions.getState(env.getGroupId());
            if ("HUMAN_TAKEOVER".equals(state)) {
                log.debug("group={} in HUMAN_TAKEOVER, silent", env.getGroupId());
                sessions.refreshTimer(env.getGroupId());
                ack.acknowledge();
                return;
            }

            boolean mentioned = isMentioned(env.getMentionedList());
            if (!mentioned) {
                ack.acknowledge();
                return;
            }

            String stripped = stripAtMention(env.getContent());
            if (stripped == null || stripped.isBlank()) {
                dialog.sendGuide(env.getGroupId());
                ack.acknowledge();
                return;
            }

            // 输入敏感词前置检查
            CheckResult in = sensitive.checkInbound(env.getGroupId(), stripped);
            if (in.isBlocked()) {
                dialog.sendBlockedAndHandoff(env);
                ack.acknowledge();
                return;
            }
            stripped = in.getProcessedText();

            // 取上下文快照（排除当前触发消息本身）
            List<ContextSnapshot.HistoryItem> history = ctx.snapshot(env.getGroupId(), env.getMsgId());
            ContextSnapshot snap = new ContextSnapshot(
                    env.getGroupId(),
                    new ContextSnapshot.TriggerMsg(env.getMsgId(), env.getFromUserid(), env.getFromUserid(), stripped),
                    history,
                    env.getConversationId(),
                    env.getCsAgentId());

            dialog.dispatch(snap);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("inbound worker failed msg={}", env.getMsgId(), e);
            ack.acknowledge();   // MVP: ack 避免无限重试；真实生产应走 DLQ
        } finally {
            MDC.remove("conversationId");
            TenantContext.clear();
        }
    }

    private boolean isMentioned(List<String> mentionedList) {
        if (mentionedList == null) return false;
        String bot = props.getWecom().getBotUserid();
        return bot != null && mentionedList.stream().anyMatch(bot::equalsIgnoreCase);
    }

    /** 去掉 `@xxx ` 前缀（兼容全/半角空格）。 */
    static String stripAtMention(String content) {
        if (content == null) return null;
        // 兼容多个 @ 开头
        String s = content.replaceAll("(?m)^(\\s*@\\S+[\\s\\u3000])+", "").trim();
        // 兼容中间位置的 @bot
        s = s.replaceAll("@\\S+", "").trim();
        return s;
    }
}
