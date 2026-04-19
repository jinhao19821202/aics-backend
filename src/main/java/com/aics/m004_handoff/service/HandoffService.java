package com.aics.m004_handoff.service;

import com.aics.common.tenant.TenantContext;
import com.aics.config.AppProperties;
import com.aics.infra.wecom.WeComApiClient;
import com.aics.m004_handoff.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * M004 兜底/接管 + 关闭。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffService {

    private final HandoffRecordRepository handoffRepo;
    private final GroupAgentMappingRepository mappingRepo;
    private final GroupSessionService sessionService;
    private final WeComApiClient wecom;
    private final StringRedisTemplate redis;
    private final AppProperties props;

    private static final Duration DEDUP = Duration.ofMinutes(10);

    @Transactional
    public HandoffRecord trigger(String groupId, String reason, String lastQuery, String conversationId) {
        Long tenantId = TenantContext.require();
        String dedupKey = "dedup:handoff:t" + tenantId + ":" + groupId;
        Boolean firstTime = redis.opsForValue().setIfAbsent(dedupKey, "1", DEDUP);
        if (!Boolean.TRUE.equals(firstTime)) {
            String text = "客服已收到通知，马上为您处理～";
            wecom.sendTextToGroup(groupId, text);
            return null;
        }

        // 找 agents
        Optional<GroupAgentMapping> m = mappingRepo.findByTenantIdAndGroupId(tenantId, groupId);
        List<String> agents = m.map(GroupAgentMapping::getAgentUserids).orElse(List.of());
        String fallbackAgent = m.map(GroupAgentMapping::getDefaultAgent).orElse(null);

        String agent = null;
        if (!agents.isEmpty()) {
            String rrKey = "handoff:rr:t" + tenantId + ":" + groupId;
            Long idx = redis.opsForValue().increment(rrKey);
            if (idx == null) idx = 0L;
            agent = agents.get((int) (idx % agents.size()));
        } else if (fallbackAgent != null) {
            agent = fallbackAgent;
        }

        HandoffRecord rec = new HandoffRecord();
        rec.setTenantId(tenantId);
        rec.setGroupId(groupId);
        rec.setTriggerReason(reason);
        rec.setAgentUserid(agent);
        rec.setTriggerPayload(Map.of(
                "lastQuery", lastQuery == null ? "" : lastQuery,
                "conversationId", conversationId == null ? "" : conversationId));
        rec = handoffRepo.save(rec);

        sessionService.markHumanTakeover(groupId, rec.getId());

        String notice = buildNotice(reason, lastQuery, agent);
        wecom.sendTextToGroup(groupId, notice);

        log.info("handoff triggered: group={}, reason={}, agent={}, recId={}", groupId, reason, agent, rec.getId());
        return rec;
    }

    @Transactional
    public void close(Long handoffId, String closeReason) {
        handoffRepo.findById(handoffId).ifPresent(h -> {
            int affected = handoffRepo.close(handoffId, OffsetDateTime.now(), closeReason);
            if (affected > 0) {
                sessionService.backToBot(h.getGroupId());
                wecom.sendTextToGroup(h.getGroupId(),
                        String.format("人工会话已结束，我是智能助手%s，如有问题欢迎 @我继续提问～", props.getBotName()));
            }
        });
    }

    @Transactional
    public void closeByGroup(String groupId, String closeReason) {
        Long tenantId = TenantContext.require();
        Optional<HandoffRecord> cur = handoffRepo.findFirstByTenantIdAndGroupIdAndClosedAtIsNullOrderByTriggeredAtDesc(tenantId, groupId);
        cur.ifPresent(h -> close(h.getId(), closeReason));
    }

    private String buildNotice(String reason, String lastQuery, String agent) {
        String reasonText = switch (reason) {
            case "USER_REQUEST" -> "客户主动要求转人工";
            case "LOW_CONFIDENCE", "REPEATED_LOWCONF" -> "机器人未找到明确答案";
            case "LLM_FAIL", "LLM_TIMEOUT", "CIRCUIT_OPEN" -> "系统临时异常";
            case "SENSITIVE_OUTPUT" -> "答复内容涉及敏感";
            default -> "需要人工协助";
        };
        String mention = agent == null || agent.isBlank() ? "" : "@" + agent + " ";
        return String.format("🔔 该客户问题需要人工协助：\n- 原因：%s\n- 最近提问：%s\n\n%s请跟进～\n（请在 30 分钟内响应，超时自动关闭由机器人接管）",
                reasonText, lastQuery == null ? "" : lastQuery, mention);
    }
}
