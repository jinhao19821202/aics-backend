package com.aics.m005_admin.sensitive;

import com.aics.common.tenant.TenantContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 敏感词过滤器（多租户）：每租户一份 DFA，Redis pub/sub 热刷新。
 * 入口 BLOCK→阻断转人工，ALERT/MASK→透传（带标记）；出口 BLOCK→阻断，MASK→替换。
 * 命中写 sensitive_hit_log 审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService implements MessageListener {

    public static final String RELOAD_CHANNEL = "sensitive:reload";

    private final SensitiveWordRepository repo;
    private final SensitiveHitLogRepository logRepo;
    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer listenerContainer;

    /** 每租户一份 DFA。惰性加载，热刷新时清空。 */
    private final ConcurrentMap<Long, DfaFilter> filters = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        listenerContainer.addMessageListener(this, new ChannelTopic(RELOAD_CHANNEL));
        log.info("SensitiveWordService initialized, subscribing to {}", RELOAD_CHANNEL);
    }

    @PreDestroy
    public void destroy() {
        try {
            listenerContainer.removeMessageListener(this);
        } catch (Exception ignore) {}
    }

    /** 清空所有租户缓存；下次检查时各自重建。 */
    public void reload() {
        filters.clear();
        log.info("sensitive words cache cleared for all tenants");
    }

    public void publishReload() {
        redis.convertAndSend(RELOAD_CHANNEL, "reload");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("received sensitive reload signal: {}", payload);
        reload();
    }

    public CheckResult checkInbound(String groupId, String text) {
        return doCheck(groupId, text, "INBOUND");
    }

    public CheckResult checkOutbound(String groupId, String text) {
        return doCheck(groupId, text, "OUTBOUND");
    }

    private DfaFilter filterFor(Long tenantId) {
        return filters.computeIfAbsent(tenantId, tid -> new DfaFilter(repo.findByTenantIdAndEnabledTrue(tid)));
    }

    private CheckResult doCheck(String groupId, String text, String direction) {
        if (text == null || text.isEmpty()) return CheckResult.pass(text);
        Long tenantId = TenantContext.require();
        List<DfaFilter.Hit> hits = filterFor(tenantId).match(text);
        if (hits.isEmpty()) return CheckResult.pass(text);

        String action = highestAction(hits);
        StringBuilder masked = new StringBuilder(text);
        boolean blocked = "BLOCK".equals(action);

        for (DfaFilter.Hit h : hits) {
            if ("MASK".equals(h.meta.action) || "BLOCK".equals(h.meta.action)) {
                for (int i = h.start; i < h.end; i++) masked.setCharAt(i, '*');
            }
            logHit(tenantId, groupId, h, direction);
        }

        if (blocked) {
            return CheckResult.blocked(masked.toString(), hits.get(0).meta.word, hits.get(0).meta.level);
        }
        return CheckResult.pass(masked.toString());
    }

    private String highestAction(List<DfaFilter.Hit> hits) {
        boolean hasBlock = false, hasMask = false;
        for (DfaFilter.Hit h : hits) {
            if ("BLOCK".equals(h.meta.action)) hasBlock = true;
            else if ("MASK".equals(h.meta.action)) hasMask = true;
        }
        if (hasBlock) return "BLOCK";
        if (hasMask) return "MASK";
        return "ALERT";
    }

    private void logHit(Long tenantId, String groupId, DfaFilter.Hit hit, String direction) {
        try {
            SensitiveHitLog entry = new SensitiveHitLog();
            entry.setTenantId(tenantId);
            entry.setDirection(direction);
            entry.setGroupId(groupId);
            entry.setWord(hit.meta.word);
            entry.setLevel(hit.meta.level);
            entry.setActionTaken(hit.meta.action);
            entry.setOriginalSnippet(safeSnippet(hit));
            logRepo.save(entry);
        } catch (Exception e) {
            log.warn("sensitive hit log save failed: {}", e.getMessage());
        }
    }

    private String safeSnippet(DfaFilter.Hit h) {
        String w = h.meta.word;
        if (w == null) return null;
        return w.length() > 64 ? w.substring(0, 64) : w;
    }

    public static class CheckResult {
        private final boolean blocked;
        private final String processedText;
        private final String hitWord;
        private final String level;

        private CheckResult(boolean blocked, String processedText, String hitWord, String level) {
            this.blocked = blocked;
            this.processedText = processedText;
            this.hitWord = hitWord;
            this.level = level;
        }

        public static CheckResult pass(String text) { return new CheckResult(false, text, null, null); }
        public static CheckResult blocked(String text, String word, String level) {
            return new CheckResult(true, text, word, level);
        }

        public boolean isBlocked() { return blocked; }
        public String getProcessedText() { return processedText; }
        public String getHitWord() { return hitWord; }
        public String getLevel() { return level; }
    }
}
